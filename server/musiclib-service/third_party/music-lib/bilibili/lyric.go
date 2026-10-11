package bilibili

import (
	"errors"
	"qmusic-musiclib/third_party/music-lib/model"
)

func GetLyrics(s *model.Song) (string, error) { return defaultBilibili.GetLyrics(s) }

func (b *Bilibili) GetLyrics(s *model.Song) (string, error) {
	if s.Source != "bilibili" {
		return "", errors.New("source mismatch")
	}
	return "", nil
}
