package joox

import "qmusic-musiclib/third_party/music-lib/model"

func GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return defaultJoox.GetUserPlaylists(page, limit)
}

func (p *Joox) GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return nil, model.ErrUserPlaylistsUnsupported
}
