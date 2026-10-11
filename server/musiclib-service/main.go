// Command qmusic-musiclib 是 qMusic 的多音源聚合服务。
//
// 它基于开源项目 github.com/guohuiyuan/music-lib（AGPL-3.0），把
// 网易云 / QQ / 酷狗 / 酷我 / 咪咕 / 千千 / 汽水 / B站 / Apple Music
// 等平台的搜索、直链解析、歌词能力统一成一组 HTTP JSON 接口，
// 供 qMusic 的 Node 抓取服务与 Android 客户端调用。
//
// 设计要点：
//   - 每个平台互相独立，任意平台失败不影响其它平台；
//   - 能直链的平台直接返回 CDN 地址（最省流量）；
//   - 需要解密的平台（汽水、咪咕 Z3D）由本服务下载解密后以
//     HTTP Range 的方式回源，ExoPlayer 可直接边下边播、可拖动进度。
package main

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"qmusic-musiclib/third_party/music-lib/apple"
	"qmusic-musiclib/third_party/music-lib/bilibili"
	"qmusic-musiclib/third_party/music-lib/kugou"
	"qmusic-musiclib/third_party/music-lib/kuwo"
	"qmusic-musiclib/third_party/music-lib/migu"
	"qmusic-musiclib/third_party/music-lib/model"
	"qmusic-musiclib/third_party/music-lib/netease"
	"qmusic-musiclib/third_party/music-lib/qianqian"
	"qmusic-musiclib/third_party/music-lib/qq"
	"qmusic-musiclib/third_party/music-lib/soda"
)

const version = "0.1.0"

// source 描述一个音源的能力集合。
// 所有方法都来自 music-lib 的包级函数，方便按需增删平台。
type source struct {
	ID        string // 音源标识（与客户端 source 参数一致）
	Name      string // 中文名，用于客户端展示
	Search    func(string) ([]model.Song, error)
	Download  func(*model.Song) (string, error)
	Lyric     func(*model.Song) (string, error)
	Playlists func(string) ([]model.Playlist, error)
	Songs     func(string) ([]model.Song, error)
	// RequiresProxy 表示该平台无法直接给出可播放直链，必须由本服务解密回源
	RequiresProxy bool
}

var sources = map[string]*source{}

// 客户端展示顺序
var sourceOrder = []string{
	"netease", "qq", "kugou", "kuwo", "migu", "qianqian", "soda", "bilibili", "apple",
}

func register(s *source) { sources[s.ID] = s }

func init() {
	register(&source{
		ID: "netease", Name: "网易云音乐",
		Search: netease.Search, Download: netease.GetDownloadURL, Lyric: netease.GetLyrics,
		Playlists: netease.SearchPlaylist, Songs: netease.GetPlaylistSongs,
	})
	register(&source{
		ID: "qq", Name: "QQ 音乐",
		Search: qq.Search, Download: qq.GetDownloadURL, Lyric: qq.GetLyrics,
		Playlists: qq.SearchPlaylist, Songs: qq.GetPlaylistSongs,
	})
	register(&source{
		ID: "kugou", Name: "酷狗音乐",
		Search: kugou.Search, Download: kugou.GetDownloadURL, Lyric: kugou.GetLyrics,
		Playlists: kugou.SearchPlaylist, Songs: kugou.GetPlaylistSongs,
	})
	register(&source{
		ID: "kuwo", Name: "酷我音乐",
		Search: kuwo.Search, Download: kuwo.GetDownloadURL, Lyric: kuwo.GetLyrics,
		Playlists: kuwo.SearchPlaylist, Songs: kuwo.GetPlaylistSongs,
	})
	register(&source{
		ID: "migu", Name: "咪咕音乐",
		Search: migu.Search, Download: migu.GetDownloadURL, Lyric: migu.GetLyrics,
		Playlists: migu.SearchPlaylist, Songs: migu.GetPlaylistSongs,
	})
	register(&source{
		ID: "qianqian", Name: "千千音乐",
		Search: qianqian.Search, Download: qianqian.GetDownloadURL, Lyric: qianqian.GetLyrics,
		Playlists: qianqian.SearchPlaylist, Songs: qianqian.GetPlaylistSongs,
	})
	register(&source{
		ID: "soda", Name: "汽水音乐",
		Search: soda.Search, Download: soda.GetDownloadURL, Lyric: soda.GetLyrics,
		Playlists: soda.SearchPlaylist, Songs: soda.GetPlaylistSongs,
		RequiresProxy: true,
	})
	register(&source{
		ID: "bilibili", Name: "Bilibili",
		Search: bilibili.Search, Download: bilibili.GetDownloadURL, Lyric: bilibili.GetLyrics,
		Playlists: bilibili.SearchPlaylist, Songs: bilibili.GetPlaylistSongs,
	})
	register(&source{
		ID: "apple", Name: "Apple Music",
		Search: apple.Search, Download: apple.GetDownloadURL, Lyric: apple.GetLyrics,
		Playlists: apple.SearchPlaylist, Songs: apple.GetPlaylistSongs,
	})
}

// ---------- 通用结构 ----------

// SongDTO 是返回给客户端的统一歌曲结构（字段与 Android 端 Song 对齐）。
// Token 是完整 Song 的 base64 载荷，客户端在请求播放地址/歌词时原样回传，
// 这样跨平台解析所需的所有元数据都不会在 JSON 转换中丢失。
type SongDTO struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Artist   string `json:"artist"`
	Album    string `json:"album"`
	Duration int    `json:"duration"` // 秒
	Cover    string `json:"cover"`
	Source   string `json:"source"`
	Ext      string `json:"ext"`
	Link     string `json:"link"`
	Token    string `json:"token"`
}

func toDTO(s model.Song) SongDTO {
	return SongDTO{
		ID: s.ID, Name: s.Name, Artist: s.Artist, Album: s.Album,
		Duration: s.Duration, Cover: s.Cover, Source: s.Source,
		Ext: s.Ext, Link: s.Link, Token: encodeSong(s),
	}
}

func toDTOs(list []model.Song) []SongDTO {
	out := make([]SongDTO, 0, len(list))
	for _, s := range list {
		out = append(out, toDTO(s))
	}
	return out
}

// encodeSong / decodeSong：把 music-lib 的 Song 塞进 URL 查询参数，
// 这样客户端拿到的就是一个普通 http 直链，ExoPlayer 无需额外协议。
func encodeSong(s model.Song) string {
	b, _ := json.Marshal(s)
	return base64.RawURLEncoding.EncodeToString(b)
}

func decodeSong(raw string) (model.Song, error) {
	var s model.Song
	if raw == "" {
		return s, errors.New("missing song payload")
	}
	b, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return s, fmt.Errorf("bad song payload: %w", err)
	}
	if err := json.Unmarshal(b, &s); err != nil {
		return s, fmt.Errorf("bad song json: %w", err)
	}
	if s.Source == "" {
		s.Source = "netease"
	}
	return s, nil
}

// ---------- HTTP ----------

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]any{"ok": false, "error": msg})
}

func cors(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		w.Header().Set("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next(w, r)
	}
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"service": "qmusic-musiclib",
		"version": version,
		"lib":     "github.com/guohuiyuan/music-lib",
		"count":   len(sources),
	})
}

func handleSources(w http.ResponseWriter, r *http.Request) {
	list := make([]map[string]any, 0, len(sourceOrder))
	for _, id := range sourceOrder {
		s := sources[id]
		if s == nil {
			continue
		}
		list = append(list, map[string]any{
			"id": id, "name": s.Name, "requiresProxy": s.RequiresProxy,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "sources": list})
}

func handleSearch(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	id := defaultSource(q.Get("source"))
	s := sources[id]
	if s == nil || s.Search == nil {
		writeErr(w, http.StatusNotFound, "unknown source: "+id)
		return
	}
	keyword := strings.TrimSpace(q.Get("keyword"))
	if keyword == "" {
		writeErr(w, http.StatusBadRequest, "keyword is required")
		return
	}
	limit := parseInt(q.Get("limit"), 30)

	start := time.Now()
	results, err := s.Search(keyword)
	if err != nil {
		writeErr(w, http.StatusBadGateway, fmt.Sprintf("[%s] %v", id, err))
		return
	}
	if limit > 0 && len(results) > limit {
		results = results[:limit]
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ok": true, "source": id, "keyword": keyword,
		"songs": toDTOs(results), "elapsed_ms": time.Since(start).Milliseconds(),
	})
}

func handleSongURL(w http.ResponseWriter, r *http.Request) {
	song, err := decodeSong(r.URL.Query().Get("s"))
	if err != nil {
		writeErr(w, http.StatusBadRequest, err.Error())
		return
	}
	s := sources[song.Source]
	if s == nil {
		writeErr(w, http.StatusNotFound, "unknown source: "+song.Source)
		return
	}

	// 咪咕单独判断：非加密时给直链，加密（Z3D）时回源解密
	if song.Source == "migu" {
		if info, ierr := migu.GetDownloadInfo(&song); ierr == nil && !info.Encrypted && info.URL != "" {
			writeJSON(w, http.StatusOK, map[string]any{"ok": true, "url": info.URL, "proxied": false})
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "url": streamURL(r, song), "proxied": true})
		return
	}

	if s.RequiresProxy {
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "url": streamURL(r, song), "proxied": true})
		return
	}

	url, err := s.Download(&song)
	if err != nil || strings.TrimSpace(url) == "" {
		msg := "no playable url"
		if err != nil {
			msg = err.Error()
		}
		// 直链解析失败时退化为服务端回源，尽量让用户能听到
		writeJSON(w, http.StatusOK, map[string]any{
			"ok": true, "url": streamURL(r, song), "proxied": true, "fallback": msg,
		})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "url": url, "proxied": false})
}

// streamURL 生成回源地址。
// 网关（Node）会通过 X-Public-Base 告知真正对客户端可见的地址前缀，
// 否则客户端拿到的会是一个内网地址而无法播放。
func streamURL(r *http.Request, song model.Song) string {
	base := strings.TrimSuffix(r.Header.Get("X-Public-Base"), "/")
	if base == "" {
		host := r.Header.Get("X-Forwarded-Host")
		if host == "" {
			host = r.Host
		}
		base = "http://" + host
	}
	return fmt.Sprintf("%s/stream?s=%s", base, encodeSong(song))
}

func handleLyric(w http.ResponseWriter, r *http.Request) {
	song, err := decodeSong(rawSongParam(r))
	if err != nil {
		writeErr(w, http.StatusBadRequest, err.Error())
		return
	}
	s := sources[song.Source]
	if s == nil || s.Lyric == nil {
		writeErr(w, http.StatusNotFound, "unknown source: "+song.Source)
		return
	}
	lrc, err := s.Lyric(&song)
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "lrc": "", "error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "lrc": lrc})
}

func handlePlaylistSearch(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	id := defaultSource(q.Get("source"))
	s := sources[id]
	if s == nil || s.Playlists == nil {
		writeErr(w, http.StatusNotFound, "unknown source: "+id)
		return
	}
	keyword := strings.TrimSpace(q.Get("keyword"))
	if keyword == "" {
		writeErr(w, http.StatusBadRequest, "keyword is required")
		return
	}
	limit := parseInt(q.Get("limit"), 20)
	list, err := s.Playlists(keyword)
	if err != nil {
		writeErr(w, http.StatusBadGateway, fmt.Sprintf("[%s] %v", id, err))
		return
	}
	if limit > 0 && len(list) > limit {
		list = list[:limit]
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "source": id, "playlists": list})
}

func handlePlaylistSongs(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	id := defaultSource(q.Get("source"))
	s := sources[id]
	if s == nil || s.Songs == nil {
		writeErr(w, http.StatusNotFound, "unknown source: "+id)
		return
	}
	pid := strings.TrimSpace(q.Get("id"))
	if pid == "" {
		writeErr(w, http.StatusBadRequest, "id is required")
		return
	}
	list, err := s.Songs(pid)
	if err != nil {
		writeErr(w, http.StatusBadGateway, fmt.Sprintf("[%s] %v", id, err))
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "source": id, "songs": toDTOs(list)})
}

// handleStream 把音频（必要时先解密）以支持 Range 的方式回源给播放器。
// 同一个歌曲只会真正下载一次，之后复用磁盘缓存。
func handleStream(w http.ResponseWriter, r *http.Request) {
	raw := r.URL.Query().Get("s")
	if raw == "" {
		raw = rawSongParam(r)
	}
	song, err := decodeSong(raw)
	if err != nil {
		writeErr(w, http.StatusBadRequest, err.Error())
		return
	}
	file, err := ensureLocalFile(song)
	if err != nil {
		writeErr(w, http.StatusBadGateway, err.Error())
		return
	}
	w.Header().Set("Content-Type", mimeFor(song.Ext))
	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Cache-Control", "public, max-age=3600")
	http.ServeFile(w, r, file)
}

// rawSongParam 兼容 POST 表单 / JSON 体，便于调试与 Node 侧调用。
func rawSongParam(r *http.Request) string {
	if v := r.URL.Query().Get("s"); v != "" {
		return v
	}
	_ = r.ParseForm()
	if v := r.PostFormValue("s"); v != "" {
		return v
	}
	if r.Body != nil && strings.Contains(r.Header.Get("Content-Type"), "application/json") {
		var body struct {
			S string `json:"s"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err == nil {
			return body.S
		}
	}
	return ""
}

// ---------- 下载 / 解密 ----------

var cacheDir string

func ensureLocalFile(song model.Song) (string, error) {
	if cacheDir == "" {
		return "", errors.New("cache dir not ready")
	}
	ext := song.Ext
	if ext == "" {
		ext = "mp3"
	}
	name := fmt.Sprintf("%s_%s.%s", song.Source, sanitize(song.ID), sanitize(ext))
	full := filepath.Join(cacheDir, name)
	if st, err := os.Stat(full); err == nil && st.Size() > 0 {
		return full, nil
	}

	// 汽水音乐：官方 Download 方法内部处理解密
	if song.Source == "soda" {
		if err := soda.Download(&song, full); err != nil {
			return "", fmt.Errorf("[soda] %w", err)
		}
		if ok, _ := fileOK(full); !ok {
			return "", errors.New("[soda] download produced empty file")
		}
		return full, nil
	}

	// 咪咕：可能返回 Z3D 加密流
	if song.Source == "migu" {
		info, err := migu.GetDownloadInfo(&song)
		if err != nil {
			return "", fmt.Errorf("[migu] %w", err)
		}
		data, err := httpGet(info.URL)
		if err != nil {
			return "", fmt.Errorf("[migu] %w", err)
		}
		if info.Encrypted {
			plain, derr := migu.DecryptAudio(data, info.FileKey)
			if derr != nil {
				return "", fmt.Errorf("[migu] decrypt: %w", derr)
			}
			data = plain
		}
		if err := os.WriteFile(full, data, 0o644); err != nil {
			return "", err
		}
		return full, nil
	}

	s := sources[song.Source]
	if s == nil || s.Download == nil {
		return "", errors.New("unsupported source: " + song.Source)
	}
	url, err := s.Download(&song)
	if err != nil {
		return "", err
	}
	data, err := httpGet(url)
	if err != nil {
		return "", err
	}
	if err := os.WriteFile(full, data, 0o644); err != nil {
		return "", err
	}
	return full, nil
}

func fileOK(p string) (bool, error) {
	st, err := os.Stat(p)
	if err != nil {
		return false, err
	}
	return st.Size() > 1024, nil
}

func httpGet(url string) ([]byte, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36")
	client := &http.Client{Timeout: 90 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("http %d from upstream", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 128<<20))
}

func mimeFor(ext string) string {
	switch strings.ToLower(strings.TrimPrefix(ext, ".")) {
	case "flac":
		return "audio/flac"
	case "wav":
		return "audio/wav"
	case "m4a", "aac":
		return "audio/mp4"
	case "ogg":
		return "audio/ogg"
	case "ape":
		return "audio/ape"
	default:
		return "audio/mpeg"
	}
}

func sanitize(s string) string {
	s = path.Base(strings.TrimSpace(s))
	var b strings.Builder
	for _, r := range s {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9', r == '-', r == '_', r == '.':
			b.WriteRune(r)
		default:
			b.WriteRune('_')
		}
	}
	out := b.String()
	if out == "" || out == "." || out == ".." {
		out = "unknown"
	}
	return out
}

func defaultSource(id string) string {
	if strings.TrimSpace(id) == "" {
		return "netease"
	}
	return strings.TrimSpace(id)
}

func parseInt(s string, def int) int {
	if s == "" {
		return def
	}
	v, err := strconv.Atoi(s)
	if err != nil || v <= 0 {
		return def
	}
	return v
}

// ---------- 网络传输层 ----------

// installSmartTransport 安装「直连优先、失败回退环境代理」的传输层。
//
// 背景：酷狗 / 酷我 / 网易云的部分接口走的是纯 HTTP（非 HTTPS），
// 而很多代理（公司网关、goproxy 之类的本地代理）只放行 HTTPS CONNECT，
// 对纯 HTTP 请求会立刻返回失败。music-lib 内部用的是默认传输层，
// 会被 HTTP_PROXY/HTTPS_PROXY 环境变量接管，于是这些音源「静默」搜不到歌。
//
// 做法：默认直连；直连失败（或 5xx）且请求没有 body 时，再用环境代理重试一次。
// 这样无论用户是直连、还是必须走代理的网络，都能正常工作。
func installSmartTransport() {
	base, ok := http.DefaultTransport.(*http.Transport)
	if !ok {
		return
	}
	direct := base.Clone()
	direct.Proxy = nil

	var proxied *http.Transport
	if os.Getenv("MUSICLIB_USE_PROXY") == "1" {
		// 强制走环境代理（企业内网需要时打开）
		http.DefaultTransport = base.Clone()
		log.Printf("已按 MUSICLIB_USE_PROXY=1 使用环境代理")
		return
	}
	if _, hasProxy := os.LookupEnv("HTTP_PROXY"); hasProxy {
		proxied = base.Clone()
	} else if _, hasProxy := os.LookupEnv("http_proxy"); hasProxy {
		proxied = base.Clone()
	}

	if proxied == nil {
		http.DefaultTransport = direct
		return
	}
	http.DefaultTransport = &fallbackTransport{direct: direct, proxied: proxied}
}

// fallbackTransport 先直连，失败后再走代理。
type fallbackTransport struct {
	direct  *http.Transport
	proxied *http.Transport
}

func (t *fallbackTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	resp, err := t.direct.RoundTrip(req)
	if err == nil && resp.StatusCode < 500 {
		return resp, nil
	}
	// 只有无 body 的请求（基本都是 GET）才能安全重放
	replayable := req.Body == nil || req.Body == http.NoBody
	if !replayable || t.proxied == nil {
		return resp, err
	}
	if resp != nil {
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))
		resp.Body.Close()
	}
	return t.proxied.RoundTrip(req.Clone(req.Context()))
}

func main() {
	installSmartTransport()

	addr := os.Getenv("MUSICLIB_ADDR")
	if addr == "" {
		port := os.Getenv("MUSICLIB_PORT")
		if port == "" {
			port = "3100"
		}
		addr = "0.0.0.0:" + port
	}

	dir := os.Getenv("MUSICLIB_CACHE")
	if dir == "" {
		dir = filepath.Join(os.TempDir(), "qmusic-musiclib-cache")
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		log.Fatalf("无法创建缓存目录 %s: %v", dir, err)
	}
	cacheDir = dir

	mux := http.NewServeMux()
	mux.HandleFunc("/health", cors(handleHealth))
	mux.HandleFunc("/sources", cors(handleSources))
	mux.HandleFunc("/search", cors(handleSearch))
	mux.HandleFunc("/song/url", cors(handleSongURL))
	mux.HandleFunc("/lyric", cors(handleLyric))
	mux.HandleFunc("/stream", handleStream) // 播放器直连，带 Range
	mux.HandleFunc("/playlist/search", cors(handlePlaylistSearch))
	mux.HandleFunc("/playlist/songs", cors(handlePlaylistSongs))

	srv := &http.Server{
		Addr:              addr,
		Handler:           logMiddleware(mux),
		ReadHeaderTimeout: 15 * time.Second,
	}
	log.Printf("qmusic-musiclib %s 已启动: http://%s  (音源 %d 个, 缓存 %s)", version, addr, len(sources), dir)
	if err := srv.ListenAndServe(); err != nil {
		log.Fatal(err)
	}
}

func logMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		// /stream 会持续回源，日志保持简短
		log.Printf("%s %s (%s)", r.Method, r.URL.Path, time.Since(start).Round(time.Millisecond))
	})
}
