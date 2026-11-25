package jp.tsuzutore.app.desktop

import jp.tsuzutore.app.data.BackupCodec
import jp.tsuzutore.app.data.BackupDocument
import jp.tsuzutore.app.domain.AppSettings
import jp.tsuzutore.app.domain.initialTemplate
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** The PC target owns an isolated JSON file; it never opens Android's database. */
class DesktopStore(dataPath: Path) : Closeable {
    val path: Path = dataPath.toAbsolutePath().normalize()
    private val lockChannel: FileChannel
    private val fileLock: FileLock
    private var closed = false

    init {
        Files.createDirectories(path.parent)
        lockChannel = FileChannel.open(path.resolveSibling(path.fileName.toString() + ".lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        try {
            fileLock = try { lockChannel.tryLock() } catch (_: OverlappingFileLockException) { null }
                ?: throw IOException("PC版が既に起動しています。先に開いたウィンドウを使用してください。")
        } catch (error: Throwable) {
            lockChannel.close()
            throw error
        }
    }

    @Synchronized
    fun load(): BackupDocument {
        check(!closed) { "保存先は閉じています。" }
        if (!Files.exists(path)) return defaults()
        // Invalid existing data is reported without renaming, replacing or deleting it.
        return readDocument(path)
    }

    @Synchronized
    fun save(document: BackupDocument) {
        check(!closed) { "保存先は閉じています。" }
        writeDocument(path, document)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try { fileLock.release() } finally { lockChannel.close() }
    }

    companion object {
        fun defaults() = BackupDocument(BackupCodec.SCHEMA_VERSION, AppSettings(), listOf(initialTemplate()), emptyList())

        fun readDocument(path: Path): BackupDocument {
            require(Files.size(path) <= BackupCodec.MAX_BYTES) { "バックアップは8MB以内のファイルを選んでください。" }
            val bytes = Files.newInputStream(path).use { it.readNBytes(BackupCodec.MAX_BYTES + 1) }
            require(bytes.size <= BackupCodec.MAX_BYTES) { "バックアップは8MB以内のファイルを選んでください。" }
            return BackupCodec.decode(String(bytes, Charsets.UTF_8))
        }

        fun writeDocument(path: Path, document: BackupDocument) {
            val bytes = BackupCodec.encode(document).toByteArray(Charsets.UTF_8)
            val target = path.toAbsolutePath().normalize()
            Files.createDirectories(target.parent)
            val temporary = Files.createTempFile(target.parent, ".tsuzutore-", ".tmp")
            try {
                FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                    val buffer = ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                // On a filesystem without atomic replacement, fail while leaving the previous file intact.
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}