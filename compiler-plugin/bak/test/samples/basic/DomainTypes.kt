package samples.basic

interface Repository<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

data class User(val id: String, val name: String)