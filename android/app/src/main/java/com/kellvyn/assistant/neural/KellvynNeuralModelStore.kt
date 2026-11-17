package com.kellvyn.assistant.neural

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * App-private atomic persistence for the trained tokenizer + neural model bundle.
 *
 * A temporary file is fully written and fsynced before replacing the previous valid bundle.
 * A backup is kept until the replacement is committed, allowing recovery after interruption.
 */
internal class KellvynNeuralModelStore(
    private val rootDirectory: File
) {
    private val lock = Any()

    fun save(result: KellvynTrainingResult) = synchronized(lock) {
        val root = requireNotNull(prepareRoot())
        val target = checkedChild(root, MODEL_FILE)
        val temporary = checkedChild(root, TEMP_FILE)
        val backup = checkedChild(root, BACKUP_FILE)

        if (temporary.exists() && !temporary.delete()) {
            error("Não foi possível remover o pacote temporário anterior.")
        }
        FileOutputStream(temporary).use { output ->
            KellvynNeuralCheckpointBundle.writeCheckpoint(result, output)
            output.fd.sync()
        }
        check(temporary.isFile && temporary.length() in 1..MAX_BUNDLE_BYTES.toLong()) {
            "O pacote neural temporário ficou vazio ou excedeu o limite."
        }

        if (backup.exists() && !backup.delete()) {
            temporary.delete()
            error("Não foi possível preparar o backup do cérebro anterior.")
        }
        if (target.exists() && !target.renameTo(backup)) {
            temporary.delete()
            error("Não foi possível preservar o último pacote neural válido.")
        }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            temporary.delete()
            error("Não foi possível confirmar o novo pacote neural.")
        }

        if (backup.exists() && !backup.delete()) {
            // The new target is already committed; a stale backup is safe to clean next time.
        }
    }

    fun load(): KellvynRestoredNeuralBundle? = synchronized(lock) {
        val root = prepareRoot(create = false) ?: return@synchronized null
        val target = checkedChild(root, MODEL_FILE)
        val backup = checkedChild(root, BACKUP_FILE)

        if (target.isFile) {
            try {
                return@synchronized readBundle(target)
            } catch (primaryError: Exception) {
                if (!backup.isFile) throw primaryError
                val restored = readBundle(backup)
                val damaged = checkedChild(root, DAMAGED_FILE)
                if (damaged.exists()) damaged.delete()
                if (!target.renameTo(damaged)) {
                    // Keep the verified backup available even if the damaged file cannot move.
                    return@synchronized restored
                }
                if (!backup.renameTo(target)) {
                    damaged.renameTo(target)
                    return@synchronized restored
                }
                damaged.delete()
                return@synchronized restored
            }
        }

        if (backup.isFile) {
            val restored = readBundle(backup)
            if (!backup.renameTo(target)) return@synchronized restored
            return@synchronized restored
        }
        null
    }

    /** Saves an independently versioned KBN-003 Transformer bundle. */
    fun saveTransformer(result: KellvynTransformerTrainingResult) = synchronized(lock) {
        val root = requireNotNull(prepareRoot())
        val target = checkedChild(root, TRANSFORMER_MODEL_FILE)
        val temporary = checkedChild(root, TRANSFORMER_TEMP_FILE)
        val backup = checkedChild(root, TRANSFORMER_BACKUP_FILE)

        if (temporary.exists() && !temporary.delete()) {
            error("Não foi possível remover o pacote Transformer temporário anterior.")
        }
        FileOutputStream(temporary).use { output ->
            KellvynTransformerCheckpointBundle.writeCheckpoint(result, output)
            output.fd.sync()
        }
        check(temporary.isFile && temporary.length() in 1..MAX_BUNDLE_BYTES.toLong()) {
            "O pacote Transformer temporário ficou vazio ou excedeu o limite."
        }

        if (backup.exists() && !backup.delete()) {
            temporary.delete()
            error("Não foi possível preparar o backup Transformer anterior.")
        }
        if (target.exists() && !target.renameTo(backup)) {
            temporary.delete()
            error("Não foi possível preservar o último pacote Transformer válido.")
        }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            temporary.delete()
            error("Não foi possível confirmar o pacote Transformer.")
        }
        if (backup.exists() && !backup.delete()) {
            // The new bundle is already committed; keep the stale backup for next cleanup.
        }
    }

    /** Loads KBN-003 only after validating checksum, tokenizer, dimensions and manifest. */
    fun loadTransformer(): KellvynRestoredTransformerBundle? = synchronized(lock) {
        val root = prepareRoot(create = false) ?: return@synchronized null
        val target = checkedChild(root, TRANSFORMER_MODEL_FILE)
        val backup = checkedChild(root, TRANSFORMER_BACKUP_FILE)

        if (target.isFile) {
            try {
                return@synchronized readTransformerBundle(target)
            } catch (primaryError: Exception) {
                if (!backup.isFile) throw primaryError
                val restored = readTransformerBundle(backup)
                val damaged = checkedChild(root, TRANSFORMER_DAMAGED_FILE)
                if (damaged.exists()) damaged.delete()
                if (!target.renameTo(damaged)) return@synchronized restored
                if (!backup.renameTo(target)) {
                    damaged.renameTo(target)
                    return@synchronized restored
                }
                damaged.delete()
                return@synchronized restored
            }
        }

        if (backup.isFile) {
            val restored = readTransformerBundle(backup)
            if (!backup.renameTo(target)) return@synchronized restored
            return@synchronized restored
        }
        null
    }

    private fun readTransformerBundle(file: File): KellvynRestoredTransformerBundle {
        require(file.isFile && file.length() in 1..MAX_BUNDLE_BYTES.toLong()) {
            "O pacote Transformer salvo está vazio ou excedeu o limite."
        }
        return FileInputStream(file).use(KellvynTransformerCheckpointBundle::readCheckpoint)
    }

    fun exists(): Boolean = synchronized(lock) {
        val root = prepareRoot(create = false) ?: return@synchronized false
        checkedChild(root, MODEL_FILE).isFile || checkedChild(root, BACKUP_FILE).isFile ||
            checkedChild(root, TRANSFORMER_MODEL_FILE).isFile || checkedChild(root, TRANSFORMER_BACKUP_FILE).isFile
    }

    fun delete() = synchronized(lock) {
        val root = prepareRoot(create = false) ?: return@synchronized
        listOf(MODEL_FILE, TEMP_FILE, BACKUP_FILE, DAMAGED_FILE, TRANSFORMER_MODEL_FILE, TRANSFORMER_TEMP_FILE, TRANSFORMER_BACKUP_FILE, TRANSFORMER_DAMAGED_FILE).forEach { name ->
            val file = checkedChild(root, name)
            if (file.exists() && !file.delete()) {
                error("Não foi possível remover o arquivo neural $name.")
            }
        }
    }

    private fun readBundle(file: File): KellvynRestoredNeuralBundle {
        require(file.isFile && file.length() in 1..MAX_BUNDLE_BYTES.toLong()) {
            "O pacote neural salvo está vazio ou excedeu o limite."
        }
        return FileInputStream(file).use(KellvynNeuralCheckpointBundle::readCheckpoint)
    }

    private fun prepareRoot(create: Boolean = true): File? {
        if (!rootDirectory.exists()) {
            if (!create) return null
            check(rootDirectory.mkdirs()) { "Não foi possível criar o diretório privado do cérebro." }
        }
        check(rootDirectory.isDirectory) { "O diretório do cérebro não é uma pasta." }
        return rootDirectory.canonicalFile
    }

    private fun checkedChild(root: File, name: String): File {
        val child = File(root, name)
        check(child.canonicalFile.parentFile == root) {
            "O caminho do pacote neural saiu do diretório privado."
        }
        return child
    }

    private companion object {
        const val MODEL_FILE = "kellvyn-kbn-002.bundle"
        const val TEMP_FILE = "kellvyn-kbn-002.bundle.tmp"
        const val BACKUP_FILE = "kellvyn-kbn-002.bundle.bak"
        const val DAMAGED_FILE = "kellvyn-kbn-002.bundle.damaged"
        const val TRANSFORMER_MODEL_FILE = "kellvyn-kbn-003.bundle"
        const val TRANSFORMER_TEMP_FILE = "kellvyn-kbn-003.bundle.tmp"
        const val TRANSFORMER_BACKUP_FILE = "kellvyn-kbn-003.bundle.bak"
        const val TRANSFORMER_DAMAGED_FILE = "kellvyn-kbn-003.bundle.damaged"
        const val MAX_BUNDLE_BYTES = 70 * 1024 * 1024
    }
}
