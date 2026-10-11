package migu

import "qmusic-musiclib/third_party/music-lib/model"

func GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return defaultMigu.GetUserPlaylists(page, limit)
}

func (p *Migu) GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return nil, model.ErrUserPlaylistsUnsupported
}
