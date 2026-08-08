package github.zerorooot.nap511.bean

sealed interface FileDialogState {
    data object None : FileDialogState
    data object CreateFolder : FileDialogState
    data object Search : FileDialogState
    data object RenameFile : FileDialogState
    data object FileInfo : FileDialogState
    data object FileOrder : FileDialogState
    data object Aria2 : FileDialogState
    data object Unzip : FileDialogState
    data object UnzipPassword : FileDialogState
    data object TextBody : FileDialogState
    data object UnzipAllFile : FileDialogState
    data object CreateSelectTorrentFile : FileDialogState
}