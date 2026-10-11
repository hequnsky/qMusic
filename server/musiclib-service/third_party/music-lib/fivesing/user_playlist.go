package fivesing

import "qmusic-musiclib/third_party/music-lib/model"

func GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return defaultFivesing.GetUserPlaylists(page, limit)
}

func (p *Fivesing) GetUserPlaylists(page, limit int) ([]model.Playlist, error) {
	return nil, model.ErrUserPlaylistsUnsupported
}
