package br.com.codecacto.kmplib.firebase.firestore

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.Transaction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis

/**
 * Serviço de Firestore para operações CRUD.
 *
 * Uso:
 * ```kotlin
 * val firestoreService = FirestoreService()
 *
 * // Criar documento
 * firestoreService.setDocument("users", userId, userData)
 *
 * // Ler documento
 * val user = firestoreService.getDocument<User>("users", userId, User.serializer())
 *
 * // Observar documento
 * firestoreService.observeDocument("users", userId, User.serializer()).collect { user ->
 *     // Atualização em tempo real
 * }
 *
 * // Query
 * val activeUsers = firestoreService.query<User>(
 *     collection = "users",
 *     deserializer = User.serializer(),
 *     filters = listOf(QueryFilter.EqualTo("status", "active")),
 *     orderBy = "createdAt",
 *     descending = true,
 *     limit = 10
 * )
 * ```
 */
class FirestoreService {

    private val firestore: FirebaseFirestore = Firebase.firestore

    companion object {
        private const val TAG = "FirestoreService"
    }

    // ========================
    // CREATE / UPDATE
    // ========================

    /**
     * Cria ou substitui um documento.
     */
    suspend fun <T : Any> setDocument(
        collection: String,
        documentId: String,
        data: T,
        serializer: SerializationStrategy<T>,
        merge: Boolean = false
    ): Result<Unit> {
        return try {
            val docRef = firestore.collection(collection).document(documentId)
            docRef.set(serializer, data, merge = merge)
            AppLogger.d(TAG, "Documento criado: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao criar documento: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Cria ou substitui um documento usando Map.
     */
    suspend fun setDocument(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean = false
    ): Result<Unit> {
        return try {
            val docRef = firestore.collection(collection).document(documentId)
            if (merge) {
                docRef.set(data, merge = true)
            } else {
                docRef.set(data)
            }
            AppLogger.d(TAG, "Documento criado: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao criar documento: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Adiciona um documento com ID gerado automaticamente.
     */
    suspend fun <T : Any> addDocument(
        collection: String,
        data: T,
        serializer: SerializationStrategy<T>
    ): Result<String> {
        return try {
            val docRef = firestore.collection(collection).add(serializer, data)
            AppLogger.d(TAG, "Documento adicionado: $collection/${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao adicionar documento: $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Atualiza campos específicos de um documento.
     */
    suspend fun updateDocument(
        collection: String,
        documentId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return try {
            firestore.collection(collection).document(documentId).update(updates)
            AppLogger.d(TAG, "Documento atualizado: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao atualizar documento: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Atualiza um campo com timestamp do servidor.
     */
    suspend fun updateWithServerTimestamp(
        collection: String,
        documentId: String,
        field: String
    ): Result<Unit> {
        return updateDocument(collection, documentId, mapOf(field to FieldValue.serverTimestamp))
    }

    // ========================
    // READ
    // ========================

    /**
     * Obtém um documento.
     */
    suspend fun <T : Any> getDocument(
        collection: String,
        documentId: String,
        deserializer: DeserializationStrategy<T>
    ): Result<T?> {
        return try {
            val snapshot = firestore.collection(collection).document(documentId).get()
            if (snapshot.exists) {
                val data = snapshot.data(deserializer)
                Result.success(data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao obter documento: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Observa um documento em tempo real.
     */
    fun <T : Any> observeDocument(
        collection: String,
        documentId: String,
        deserializer: DeserializationStrategy<T>
    ): Flow<T?> {
        return firestore.collection(collection).document(documentId).snapshots.map { snapshot ->
            if (snapshot.exists) {
                try {
                    snapshot.data(deserializer)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Erro ao deserializar documento: $collection/$documentId", e)
                    null
                }
            } else {
                null
            }
        }
    }

    /**
     * Obtém todos os documentos de uma coleção.
     */
    suspend fun <T : Any> getCollection(
        collection: String,
        deserializer: DeserializationStrategy<T>
    ): Result<List<T>> {
        return try {
            val snapshot = firestore.collection(collection).get()
            val items = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data(deserializer)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Erro ao deserializar documento: ${doc.id}", e)
                    null
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao obter coleção: $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Observa uma coleção em tempo real.
     */
    fun <T : Any> observeCollection(
        collection: String,
        deserializer: DeserializationStrategy<T>
    ): Flow<List<T>> {
        return firestore.collection(collection).snapshots.map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data(deserializer)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Erro ao deserializar documento: ${doc.id}", e)
                    null
                }
            }
        }
    }

    /**
     * Aplica filtros, ordenação e limite a uma query.
     */
    private fun buildQuery(
        collection: String,
        filters: List<QueryFilter>,
        orderBy: String?,
        descending: Boolean,
        limit: Int?
    ): Query {
        var query: Query = firestore.collection(collection)

        // Aplicar filtros
        filters.forEach { filter ->
            query = when (filter) {
                is QueryFilter.EqualTo ->
                    query.where { filter.field equalTo filter.value }
                is QueryFilter.NotEqualTo ->
                    query.where { filter.field notEqualTo filter.value }
                is QueryFilter.LessThan ->
                    query.where { filter.field lessThan filter.value }
                is QueryFilter.LessThanOrEqualTo ->
                    query.where { filter.field lessThanOrEqualTo filter.value }
                is QueryFilter.GreaterThan ->
                    query.where { filter.field greaterThan filter.value }
                is QueryFilter.GreaterThanOrEqualTo ->
                    query.where { filter.field greaterThanOrEqualTo filter.value }
                is QueryFilter.ArrayContains ->
                    query.where { filter.field contains filter.value }
                is QueryFilter.In ->
                    query.where { filter.field inArray filter.values }
            }
        }

        // Ordenação
        if (orderBy != null) {
            query = if (descending) {
                query.orderBy(orderBy, dev.gitlive.firebase.firestore.Direction.DESCENDING)
            } else {
                query.orderBy(orderBy, dev.gitlive.firebase.firestore.Direction.ASCENDING)
            }
        }

        // Limite
        if (limit != null) {
            query = query.limit(limit)
        }

        return query
    }

    /**
     * Query com filtros, ordenação e limite.
     */
    suspend fun <T : Any> query(
        collection: String,
        deserializer: DeserializationStrategy<T>,
        filters: List<QueryFilter> = emptyList(),
        orderBy: String? = null,
        descending: Boolean = false,
        limit: Int? = null
    ): Result<List<T>> {
        return try {
            val query = buildQuery(collection, filters, orderBy, descending, limit)
            val snapshot = query.get()
            val items = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data(deserializer)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Erro ao deserializar documento: ${doc.id}", e)
                    null
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro na query: $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Observa uma query em tempo real.
     */
    fun <T : Any> observeQuery(
        collection: String,
        deserializer: DeserializationStrategy<T>,
        filters: List<QueryFilter> = emptyList(),
        orderBy: String? = null,
        descending: Boolean = false,
        limit: Int? = null
    ): Flow<List<T>> {
        val query = buildQuery(collection, filters, orderBy, descending, limit)

        return query.snapshots.map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data(deserializer)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Erro ao deserializar documento: ${doc.id}", e)
                    null
                }
            }
        }
    }

    // ========================
    // DELETE
    // ========================

    /**
     * Exclui um documento.
     */
    suspend fun deleteDocument(collection: String, documentId: String): Result<Unit> {
        return try {
            firestore.collection(collection).document(documentId).delete()
            AppLogger.d(TAG, "Documento excluído: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao excluir documento: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    // ========================
    // SUBCOLLECTIONS
    // ========================

    /**
     * Obtém referência para uma subcoleção.
     */
    fun subcollection(parentCollection: String, parentId: String, subcollection: String) =
        firestore.collection(parentCollection).document(parentId).collection(subcollection)

    /**
     * Adiciona documento em subcoleção.
     */
    suspend fun <T : Any> addToSubcollection(
        parentCollection: String,
        parentId: String,
        subcollectionName: String,
        data: T,
        serializer: SerializationStrategy<T>
    ): Result<String> {
        return try {
            val docRef = subcollection(parentCollection, parentId, subcollectionName).add(serializer, data)
            AppLogger.d(TAG, "Documento adicionado: $parentCollection/$parentId/$subcollectionName/${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao adicionar documento em subcoleção", e)
            Result.failure(e)
        }
    }

    /**
     * Observa subcoleção em tempo real.
     */
    fun <T : Any> observeSubcollection(
        parentCollection: String,
        parentId: String,
        subcollection: String,
        deserializer: DeserializationStrategy<T>
    ): Flow<List<T>> {
        return subcollection(parentCollection, parentId, subcollection).snapshots.map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data(deserializer)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Erro ao deserializar documento: ${doc.id}", e)
                    null
                }
            }
        }
    }

    // ========================
    // BATCH & TRANSACTION
    // ========================

    /**
     * Executa operações em batch (até 500 operações).
     */
    suspend fun batch(operations: suspend BatchScope.() -> Unit): Result<Unit> {
        return try {
            firestore.batch().apply {
                val scope = BatchScope(this, firestore)
                operations(scope)
            }.commit()
            AppLogger.d(TAG, "Batch executado com sucesso")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao executar batch", e)
            Result.failure(e)
        }
    }

    /**
     * Executa uma **transação atômica** (read-modify-write consistente).
     *
     * Diferente de [batch] (apenas escritas), uma transação permite **ler** documentos e decidir as
     * escritas com base no estado lido — tudo de forma atômica: o Firestore reexecuta o bloco se
     * algum documento lido mudar antes do commit, garantindo serialização. Use para contadores,
     * reserva de número sequencial, gates de limite e qualquer invariante "leia e então escreva".
     *
     * Regras (impostas pelo Firestore): **toda leitura deve vir antes de qualquer escrita** dentro
     * do bloco; o bloco pode ser reexecutado, então mantenha-o **puro** (sem efeitos colaterais
     * fora das operações de transação). Não capture exceções genéricas dentro do bloco se quiser
     * que o erro propague como falha da transação.
     *
     * Uso:
     * ```kotlin
     * val result = firestore.runTransaction { tx ->
     *     val acc = tx.get("accounts", uid, Account.serializer())
     *         ?: throw IllegalStateException("conta inexistente")
     *     if (!acc.isPro && acc.usage >= limit) throw LimitReachedException()
     *     val next = acc.sequence + 1
     *     tx.update("accounts", uid, mapOf("sequence" to next))
     *     next // valor de retorno da transação
     * }
     * ```
     *
     * @return [Result.success] com o valor retornado pelo bloco, ou [Result.failure] com a exceção
     *   (inclusive a lançada dentro do bloco, p.ex. o gate de limite).
     */
    suspend fun <R> runTransaction(
        block: suspend TransactionScope.(TransactionScope) -> R
    ): Result<R> {
        return try {
            val value = firestore.runTransaction {
                val scope = TransactionScope(this, firestore)
                scope.block(scope)
            }
            Result.success(value)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao executar transação", e)
            Result.failure(e)
        }
    }
}

/**
 * Filtros para queries.
 */
sealed class QueryFilter {
    abstract val field: String

    data class EqualTo(override val field: String, val value: Any?) : QueryFilter()
    data class NotEqualTo(override val field: String, val value: Any?) : QueryFilter()
    data class LessThan(override val field: String, val value: Any) : QueryFilter()
    data class LessThanOrEqualTo(override val field: String, val value: Any) : QueryFilter()
    data class GreaterThan(override val field: String, val value: Any) : QueryFilter()
    data class GreaterThanOrEqualTo(override val field: String, val value: Any) : QueryFilter()
    data class ArrayContains(override val field: String, val value: Any) : QueryFilter()
    data class In(override val field: String, val values: List<Any>) : QueryFilter()
}

/**
 * Escopo para operações em batch.
 */
class BatchScope(
    private val batch: dev.gitlive.firebase.firestore.WriteBatch,
    private val firestore: FirebaseFirestore
) {
    fun <T : Any> set(
        collection: String,
        documentId: String,
        data: T,
        serializer: SerializationStrategy<T>
    ) {
        batch.set(firestore.collection(collection).document(documentId), serializer, data)
    }

    fun update(collection: String, documentId: String, updates: Map<String, Any?>) {
        batch.update(firestore.collection(collection).document(documentId), updates)
    }

    fun delete(collection: String, documentId: String) {
        batch.delete(firestore.collection(collection).document(documentId))
    }
}

/**
 * Escopo para operações dentro de uma transação atômica ([FirestoreService.runTransaction]).
 *
 * Espelha o [BatchScope] (mesma assinatura `collection`/`documentId`), porém **adiciona leituras**:
 * o método [get] permite ler um documento e decidir as escritas com base no estado lido.
 *
 * **Importante:** o Firestore exige que **todas as leituras ([get]/[exists]) venham antes** de
 * qualquer escrita ([set]/[update]/[delete]) no mesmo bloco. O bloco pode ser reexecutado.
 */
class TransactionScope(
    private val transaction: Transaction,
    private val firestore: FirebaseFirestore
) {
    /**
     * Lê um documento dentro da transação e o desserializa. Retorna `null` se não existir.
     * DEVE ser chamado antes de qualquer escrita no bloco.
     */
    suspend fun <T : Any> get(
        collection: String,
        documentId: String,
        deserializer: DeserializationStrategy<T>
    ): T? {
        val snapshot = transaction.get(firestore.collection(collection).document(documentId))
        return if (snapshot.exists) snapshot.data(deserializer) else null
    }

    /**
     * Verifica a existência de um documento dentro da transação (leitura).
     * DEVE ser chamado antes de qualquer escrita no bloco.
     */
    suspend fun exists(collection: String, documentId: String): Boolean =
        transaction.get(firestore.collection(collection).document(documentId)).exists

    /** Cria/substitui um documento (tipado) dentro da transação. */
    fun <T : Any> set(
        collection: String,
        documentId: String,
        data: T,
        serializer: SerializationStrategy<T>,
        merge: Boolean = false
    ) {
        transaction.set(
            firestore.collection(collection).document(documentId),
            serializer,
            data,
            merge = merge
        )
    }

    /** Cria/substitui um documento via Map dentro da transação. */
    fun set(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean = false
    ) {
        transaction.set(firestore.collection(collection).document(documentId), data, merge = merge)
    }

    /** Atualiza campos específicos (aceita dotted-paths) dentro da transação. */
    fun update(collection: String, documentId: String, updates: Map<String, Any?>) {
        val pairs = updates.entries.map { it.key to it.value }.toTypedArray()
        transaction.update(firestore.collection(collection).document(documentId), *pairs)
    }

    /** Exclui um documento dentro da transação. */
    fun delete(collection: String, documentId: String) {
        transaction.delete(firestore.collection(collection).document(documentId))
    }
}
