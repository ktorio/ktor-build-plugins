package io.ktor.samples.openapi

import kotlinx.serialization.Serializable

interface Repository<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

interface Entity {
    val id: String
}

class ListRepository<E: Entity>: Repository<E> {
    private val list: MutableList<E> = mutableListOf()

    override fun list(query: Map<String, List<String>>): List<E> {
        return list.toList()
    }

    override fun get(id: String): E? {
        return list.find { it.id == id }
    }

    override fun save(entity: E) {
        list.add(entity)
    }

    override fun delete(id: String) {
        list.removeIf { it.id == id }
    }
}

@Serializable
data class User(override val id: String, val name: String): Entity