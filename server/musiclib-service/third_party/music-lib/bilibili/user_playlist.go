package bilibili

import (
	"qmusic-musiclib/third_party/music-lib/model"
)

func GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return defaultBilibili.GetUserPlaylists(page, limit)
}

func (b *Bilibili) GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return nil, model.ErrUserPlaylistsUnsupported
}
