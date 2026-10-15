const VLC_PORT = process.env.VUE_APP_VLC_PORT

const androidIcons = {
    vlc: "ic_icon",
    video: "ic_video",
    folder: "ic_folder",
    playlist: "ic_playlist",
    more: "ic_nav_more",
    audio: "ic_menu_audio",
    group: "ic_group",
    playAll: "ic_play_all",
    resumePlayback: 'ic_resume_playback',
    artist: 'ic_no_artist',
    album: 'ic_album',
    genre: 'ic_genre',
    empty: 'ic_empty',
}
const desktopIcons = {
    vlc: "ic_icon",
    video: "ic_video",
    folder: "ic_folder",
    playlist: "ic_playlist",
    more: "ic_nav_more",
    audio: "ic_menu_audio",
    group: "ic_group",
    playAll: "ic_ctx_play_all",
    resumePlayback: 'ic_resume_playback',
    artist: 'ic_no_artist',
    album: 'ic_album',
    genre: 'ic_genre',
    empty: 'ic_empty',
}

function getPortArray() {
    switch (VLC_PORT) {
        case 'desktop':
            return desktopIcons
        default:
            return androidIcons
    }
}

export const vlcIcons = {
    vlcIcon: getPortArray().vlc,
    videoIcon: getPortArray().video,
    folderIcon: getPortArray().folder,
    playlistIcon: getPortArray().playlist,
    moreIcon: getPortArray().more,
    audioIcon: getPortArray().audio,
    groupIcon: getPortArray().group,
    playAllIcon: getPortArray().playAll,
    resumePlaybackIcon: getPortArray().resumePlayback,
    artistIcon: getPortArray().artist,
    albumIcon: getPortArray().album,
    genreIcon: getPortArray().genre,
    emptyIcon: getPortArray().empty,
}
