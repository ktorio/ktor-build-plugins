// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.util.toMap
import kotlinx.serialization.*

fun Application.simpleNested(
    repository: Repository0<User0>,
    departmentRepository: Repository0<Department>,
    projectRepository: Repository0<Project>
) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {
            userEndpoints(repository)
            departmentEndpoints(departmentRepository)
            projectEndpoints(projectRepository)
        }
    }
}

private fun Route.userEndpoints(repository: Repository0<User0>) {
    route("/users") {
        /**
         * Get a list of users.
         *
         * @query q [String] Search query
         * @query limit [Int] Max items to return
         *   minimum: 1
         *   maximum: 100
         * @query sort [String] Sort field (id, name, department)
         * @query order [String] Sort order (asc, desc)
         * @response 200 [User0]+ A list of users.
         */
        get {
            val query = call.request.queryParameters.toMap()
            val list = repository.list(query)
            call.respond(list)
        }

        /**
         * Get a single user
         *
         * @path id [Int] The ID of the user
         * @response 200 [UserWithDetails] Detailed user information with related entities
         * @response 400 Bad ID argument
         * @response 404 The user was not found
         */
        get("{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val user = repository.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            val userWithDetails = UserWithDetails(
                id = user.id,
                name = user.name,
                department = Department(1, "Engineering"),
                contact = ContactInfo(
                    email = "user${user.id}@example.com",
                    phone = "+1-555-${user.id}",
                    address = Address(
                        street = "123 Main St",
                        city = "Cityville",
                        zipCode = "12345",
                        country = "USA"
                    )
                ),
                skills = listOf(
                    Skill(1, "Java", 5),
                    Skill(2, "Kotlin", 4)
                ),
                projects = listOf(
                    ProjectSummary(1, "Project Alpha"),
                    ProjectSummary(2, "Project Beta")
                ),
                metadata = mapOf(
                    "joinDate" to "2023-01-15",
                    "employeeType" to "FULL_TIME",
                    "securityClearance" to "LEVEL_2"
                ),
                roles = setOf("DEVELOPER", "TEAM_LEAD")
            )

            call.respond(userWithDetails)
        }

        /**
         * Save a new user.
         *
         * @body [UserCreateRequest] the user to save with full details.
         * @response 201 User created successfully
         * @response 400 [ValidationError] Invalid user data
         */
        post {
            val userRequest = call.receive<UserCreateRequest>()

            val validationErrors = mutableListOf<FieldError>()

            if (userRequest.name.isBlank()) {
                validationErrors.add(FieldError("name", "Name cannot be empty"))
            }

            if (userRequest.departmentId <= 0) {
                validationErrors.add(FieldError("departmentId", "Invalid department ID"))
            }

            if (userRequest.contact.email.isBlank() || !userRequest.contact.email.contains("@")) {
                validationErrors.add(FieldError("contact.email", "Invalid email format"))
            }

            if (validationErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ValidationError(
                        message = "Validation failed",
                        errors = validationErrors
                    )
                )
                return@post
            }

            val newUser = User0(
                id = userRequest.id ?: (1000 + (Math.random() * 9000).toInt()),
                name = userRequest.name
            )

            repository.save(newUser)
            call.respond(HttpStatusCode.Created)
        }

        /**
         * Update a user.
         *
         * @path id [Int] The ID of the user
         * @body [UserUpdateRequestNesting] User data to update
         * @response 200 [User0] Updated user
         * @response 400 Bad ID argument or invalid data
         * @response 404 The user was not found
         */
        put("{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest)

            val updateRequest = call.receive<UserUpdateRequestNesting>()

            val existingUser = repository.get(id)
                ?: return@put call.respond(HttpStatusCode.NotFound)

            val updatedUser = User0(
                id = existingUser.id,
                name = updateRequest.name ?: existingUser.name
            )

            repository.save(updatedUser)
            call.respond(updatedUser)
        }

        /**
         * Delete a user.
         * @path id [Int] The ID of the user
         * @response 400 Bad ID argument
         * @response 204 The user was deleted
         */
        delete("{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
            repository.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * Get user's assigned projects
         *
         * @path id [Int] The ID of the user
         * @response 200 [Project]+ List of user's projects
         * @response 400 Bad ID argument
         * @response 404 User not found
         */
        get("{id}/projects") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val user = repository.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            val projects = listOf(
                Project(
                    id = 1,
                    name = "Project Alpha",
                    description = "Main project",
                    status = ProjectStatus.ACTIVE,
                    members = listOf(
                        ProjectMember(userId = user.id, role = "LEAD"),
                        ProjectMember(userId = 2, role = "DEVELOPER")
                    ),
                    tags = setOf("important", "core"),
                    startDate = "2023-01-15",
                    deadline = "2023-12-31"
                ),
                Project(
                    id = 2,
                    name = "Project Beta",
                    description = "Side project",
                    status = ProjectStatus.PLANNING,
                    members = listOf(
                        ProjectMember(userId = user.id, role = "CONTRIBUTOR")
                    ),
                    tags = setOf("research", "experimental"),
                    startDate = "2023-06-01",
                    deadline = null
                )
            )

            call.respond(projects)
        }

        /**
         * Add user to a project
         *
         * @path userId [Int] The ID of the user
         * @path projectId [Int] The ID of the project
         * @body [ProjectAssignment] Project assignment details
         * @response 200 Assignment successful
         * @response 400 Bad arguments
         * @response 404 User or project not found
         */
        put("{userId}/projects/{projectId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest)

            val projectId = call.parameters["projectId"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest)

            val assignment = call.receive<ProjectAssignment>()

            call.respond(
                AssignmentResponse(
                    userId = userId,
                    projectId = projectId,
                    role = assignment.role,
                    assignedAt = "2023-09-15T10:30:00Z"
                )
            )
        }
    }
}

private fun Route.departmentEndpoints(repository: Repository0<Department>) {
    route("/departments") {
        /**
         * Get a list of departments.
         *
         * @response 200 [Department]+ A list of departments.
         */
        get {
            val query = call.request.queryParameters.toMap()
            val list = repository.list(query)
            call.respond(list)
        }

        /**
         * Get a single department with its members
         *
         * @path id [Int] The ID of the department
         * @response 200 [DepartmentWithMembers] Department with its members
         * @response 400 Bad ID argument
         * @response 404 Department not found
         */
        get("{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val department = repository.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            val departmentWithMembers = DepartmentWithMembers(
                id = department.id,
                name = department.name,
                manager = UserDto(id = 1, name = "Department Manager"),
                members = listOf(
                    UserDto(id = 2, name = "Team Member 1"),
                    UserDto(id = 3, name = "Team Member 2"),
                    UserDto(id = 4, name = "Team Member 3")
                ),
                subDepartments = listOf(
                    Department(id = id + 100, name = "${department.name} - Team A"),
                    Department(id = id + 101, name = "${department.name} - Team B")
                ),
                metadata = mapOf(
                    "foundedDate" to "2020-03-15",
                    "budget" to "1000000",
                    "location" to "Building A, Floor 3"
                )
            )

            call.respond(departmentWithMembers)
        }

        /**
         * Create a new department with optional nested structure
         *
         * @body [DepartmentCreateRequest] The department to create
         * @response 201 Department created successfully
         * @response 400 [ValidationError] Invalid department data
         */
        post {
            val request = call.receive<DepartmentCreateRequest>()

            val validationErrors = mutableListOf<FieldError>()

            if (request.name.isBlank()) {
                validationErrors.add(FieldError("name", "Name cannot be empty"))
            }

            if (request.managerId <= 0) {
                validationErrors.add(FieldError("managerId", "Invalid manager ID"))
            }

            if (validationErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ValidationError(
                        message = "Validation failed",
                        errors = validationErrors
                    )
                )
                return@post
            }

            val newDepartment = Department(
                id = request.id ?: (1000 + (Math.random() * 9000).toInt()),
                name = request.name
            )

            repository.save(newDepartment)
            call.respond(HttpStatusCode.Created)
        }
    }
}

private fun Route.projectEndpoints(repository: Repository0<Project>) {
    route("/projects") {
        /**
         * Get a list of projects.
         *
         * @query status [String] Filter by status (PLANNING, ACTIVE, COMPLETED, CANCELLED)
         * @query tag [String] Filter by tag
         * @response 200 [Project]+ A list of projects.
         */
        get {
            val query = call.request.queryParameters.toMap()
            val list = repository.list(query)
            call.respond(list)
        }

        /**
         * Get a single project with detailed information
         *
         * @path id [Int] The ID of the project
         * @response 200 [ProjectWithDetails] Project with detailed information
         * @response 400 Bad ID argument
         * @response 404 Project not found
         */
        get("{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val project = repository.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            val projectWithDetails = ProjectWithDetails(
                id = project.id,
                name = project.name,
                description = project.description,
                status = project.status,
                department = Department(1, "Engineering"),
                members = listOf(
                    DetailedProjectMember(
                        user = UserDto(1, "John Doe"),
                        role = "LEAD",
                        joinDate = "2023-01-15",
                        permissions = setOf("EDIT", "DELETE", "ADMIN")
                    ),
                    DetailedProjectMember(
                        user = UserDto(2, "Jane Smith"),
                        role = "DEVELOPER",
                        joinDate = "2023-02-01",
                        permissions = setOf("EDIT")
                    )
                ),
                tasks = listOf(
                    Task(
                        id = 1,
                        title = "Task 1",
                        description = "Description for task 1",
                        status = TaskStatus.IN_PROGRESS,
                        assignee = UserDto(1, "John Doe"),
                        dueDate = "2023-10-15",
                        priority = TaskPriority.HIGH,
                        subtasks = listOf(
                            SubTask(1, "Subtask 1.1", true),
                            SubTask(2, "Subtask 1.2", false)
                        ),
                        comments = listOf(
                            TaskComment(
                                id = 1,
                                author = UserDto(2, "Jane Smith"),
                                text = "First comment",
                                timestamp = "2023-09-10T14:30:00Z"
                            )
                        )
                    ),
                    Task(
                        id = 2,
                        title = "Task 2",
                        description = "Description for task 2",
                        status = TaskStatus.TODO,
                        assignee = UserDto(2, "Jane Smith"),
                        dueDate = "2023-11-01",
                        priority = TaskPriority.MEDIUM,
                        subtasks = emptyList(),
                        comments = emptyList()
                    )
                ),
                milestones = listOf(
                    Milestone(
                        id = 1,
                        title = "MVP Release",
                        dueDate = "2023-11-15",
                        completed = false,
                        tasks = listOf(1, 2)
                    )
                ),
                budget = Budget(
                    amount = 50000.0,
                    currency = "USD",
                    breakdown = mapOf(
                        "development" to 30000.0,
                        "testing" to 10000.0,
                        "deployment" to 5000.0,
                        "other" to 5000.0
                    )
                ),
                timeline = Timeline(
                    startDate = "2023-01-15",
                    endDate = "2023-12-31",
                    phases = listOf(
                        TimelinePhase("Planning", "2023-01-15", "2023-02-28", true),
                        TimelinePhase("Development", "2023-03-01", "2023-10-31", false),
                        TimelinePhase("Testing", "2023-11-01", "2023-12-15", false),
                        TimelinePhase("Deployment", "2023-12-16", "2023-12-31", false)
                    )
                ),
                tags = project.tags,
                metadata = mapOf(
                    "repositoryUrl" to "https://github.com/example/project",
                    "slack" to "#project-channel"
                )
            )

            call.respond(projectWithDetails)
        }

        /**
         * Create a new project
         *
         * @body [ProjectCreateRequest] The project to create
         * @response 201 Project created successfully
         * @response 400 [ValidationError] Invalid project data
         */
        post {
            val request = call.receive<ProjectCreateRequest>()

            val validationErrors = mutableListOf<FieldError>()

            if (request.name.isBlank()) {
                validationErrors.add(FieldError("name", "Name cannot be empty"))
            }

            if (request.departmentId <= 0) {
                validationErrors.add(FieldError("departmentId", "Invalid department ID"))
            }

            if (validationErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ValidationError(
                        message = "Validation failed",
                        errors = validationErrors
                    )
                )
                return@post
            }

            val newProject = Project(
                id = request.id ?: (1000 + (Math.random() * 9000).toInt()),
                name = request.name,
                description = request.description ?: "",
                status = request.status ?: ProjectStatus.PLANNING,
                members = emptyList(),
                tags = request.tags ?: emptySet(),
                startDate = request.startDate,
                deadline = request.deadline
            )

            repository.save(newProject)
            call.respond(HttpStatusCode.Created)
        }

        /**
         * Get project tasks
         *
         * @path id [Int] The ID of the project
         * @query status [String] Filter by status (TODO, IN_PROGRESS, REVIEW, DONE)
         * @query assignee [Int] Filter by assignee ID
         * @query priority [String] Filter by priority (LOW, MEDIUM, HIGH, CRITICAL)
         * @response 200 [Task]+ List of tasks
         * @response 400 Bad ID argument
         * @response 404 Project not found
         */
        get("{id}/tasks") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val project = repository.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            val statusFilter = call.request.queryParameters["status"]
            val assigneeFilter = call.request.queryParameters["assignee"]?.toIntOrNull()
            val priorityFilter = call.request.queryParameters["priority"]

            val tasks = listOf(
                Task(
                    id = 1,
                    title = "Task 1",
                    description = "Description for task 1",
                    status = TaskStatus.IN_PROGRESS,
                    assignee = UserDto(1, "John Doe"),
                    dueDate = "2023-10-15",
                    priority = TaskPriority.HIGH,
                    subtasks = listOf(
                        SubTask(1, "Subtask 1.1", true),
                        SubTask(2, "Subtask 1.2", false)
                    ),
                    comments = listOf(
                        TaskComment(
                            id = 1,
                            author = UserDto(2, "Jane Smith"),
                            text = "First comment",
                            timestamp = "2023-09-10T14:30:00Z"
                        )
                    )
                ),
                Task(
                    id = 2,
                    title = "Task 2",
                    description = "Description for task 2",
                    status = TaskStatus.TODO,
                    assignee = UserDto(2, "Jane Smith"),
                    dueDate = "2023-11-01",
                    priority = TaskPriority.MEDIUM,
                    subtasks = emptyList(),
                    comments = emptyList()
                )
            )

            val filteredTasks = tasks.filter { task ->
                (statusFilter == null || task.status.name == statusFilter) &&
                        (assigneeFilter == null || task.assignee.id == assigneeFilter) &&
                        (priorityFilter == null || task.priority.name == priorityFilter)
            }

            call.respond(filteredTasks)
        }

        /**
         * Add a task to a project
         *
         * @path id [Int] The ID of the project
         * @body [TaskCreateRequest] The task to create
         * @response 201 Task created successfully
         * @response 400 [ValidationError] Invalid task data
         * @response 404 Project not found
         */
        post("{id}/tasks") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val project = repository.get(id)
                ?: return@post call.respond(HttpStatusCode.NotFound)

            val request = call.receive<TaskCreateRequest>()

            val validationErrors = mutableListOf<FieldError>()

            if (request.title.isBlank()) {
                validationErrors.add(FieldError("title", "Title cannot be empty"))
            }

            if (request.assigneeId <= 0) {
                validationErrors.add(FieldError("assigneeId", "Invalid assignee ID"))
            }

            if (validationErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ValidationError(
                        message = "Validation failed",
                        errors = validationErrors
                    )
                )
                return@post
            }

            call.respond(
                HttpStatusCode.Created,
                mapOf(
                    "id" to 1000,
                    "title" to request.title,
                    "projectId" to id
                )
            )
        }
    }
}

interface Repository0<E> {
    fun get(id: Int): E?
    fun save(entity: E)
    fun delete(id: Int)
    fun list(query: Map<String, List<String>>): List<E>
}

data class User0(val id: Int, val name: String)

@Serializable
data class UserDto(val id: Int, val name: String)

@Serializable
data class Department(val id: Int, val name: String)

@Serializable
enum class ProjectStatus {
    PLANNING,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

@Serializable
data class ProjectMember(val userId: Int, val role: String)

@Serializable
data class Project(
    val id: Int,
    val name: String,
    val description: String,
    val status: ProjectStatus,
    val members: List<ProjectMember>,
    val tags: Set<String>,
    val startDate: String?,
    val deadline: String?
)

@Serializable
data class ContactInfo(
    val email: String,
    val phone: String,
    val address: Address
)

@Serializable
data class Address(
    val street: String,
    val city: String,
    val zipCode: String,
    val country: String
)

@Serializable
data class Skill(
    val id: Int,
    val name: String,
    val level: Int
)

@Serializable
data class ProjectSummary(
    val id: Int,
    val name: String
)

@Serializable
data class UserWithDetails(
    val id: Int,
    val name: String,
    val department: Department,
    val contact: ContactInfo,
    val skills: List<Skill>,
    val projects: List<ProjectSummary>,
    val metadata: Map<String, String>,
    val roles: Set<String>
)

@Serializable
data class UserCreateRequest(
    val id: Int? = null,
    val name: String,
    val departmentId: Int,
    val contact: ContactInfo,
    val skills: List<Int> = emptyList(),
    val roles: Set<String> = emptySet()
)

@Serializable
data class UserUpdateRequestNesting(
    val name: String? = null,
    val departmentId: Int? = null,
    val contact: ContactInfo? = null
)

@Serializable
data class ValidationError(
    val message: String,
    val errors: List<FieldError>
)

@Serializable
data class FieldError(
    val field: String,
    val message: String
)

@Serializable
data class ProjectAssignment(
    val role: String,
    val permissions: Set<String> = emptySet()
)

@Serializable
data class AssignmentResponse(
    val userId: Int,
    val projectId: Int,
    val role: String,
    val assignedAt: String
)

@Serializable
data class DepartmentWithMembers(
    val id: Int,
    val name: String,
    val manager: UserDto,
    val members: List<UserDto>,
    val subDepartments: List<Department>,
    val metadata: Map<String, String>
)

@Serializable
data class DepartmentCreateRequest(
    val id: Int? = null,
    val name: String,
    val managerId: Int,
    val parentDepartmentId: Int? = null
)

@Serializable
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    REVIEW,
    DONE
}

@Serializable
enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class SubTask(
    val id: Int,
    val title: String,
    val completed: Boolean
)

@Serializable
data class TaskComment(
    val id: Int,
    val author: UserDto,
    val text: String,
    val timestamp: String
)

@Serializable
data class Task(
    val id: Int,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val assignee: UserDto,
    val dueDate: String?,
    val priority: TaskPriority,
    val subtasks: List<SubTask>,
    val comments: List<TaskComment>
)

@Serializable
data class DetailedProjectMember(
    val user: UserDto,
    val role: String,
    val joinDate: String,
    val permissions: Set<String>
)

@Serializable
data class Milestone(
    val id: Int,
    val title: String,
    val dueDate: String,
    val completed: Boolean,
    val tasks: List<Int>
)

@Serializable
data class Budget(
    val amount: Double,
    val currency: String,
    val breakdown: Map<String, Double>
)

@Serializable
data class TimelinePhase(
    val name: String,
    val startDate: String,
    val endDate: String,
    val completed: Boolean
)

@Serializable
data class Timeline(
    val startDate: String,
    val endDate: String,
    val phases: List<TimelinePhase>
)

@Serializable
data class ProjectWithDetails(
    val id: Int,
    val name: String,
    val description: String,
    val status: ProjectStatus,
    val department: Department,
    val members: List<DetailedProjectMember>,
    val tasks: List<Task>,
    val milestones: List<Milestone>,
    val budget: Budget,
    val timeline: Timeline,
    val tags: Set<String>,
    val metadata: Map<String, String>
)

@Serializable
data class ProjectCreateRequest(
    val id: Int? = null,
    val name: String,
    val description: String? = null,
    val departmentId: Int,
    val status: ProjectStatus? = null,
    val tags: Set<String>? = null,
    val startDate: String? = null,
    val deadline: String? = null
)

@Serializable
data class TaskCreateRequest(
    val title: String,
    val description: String? = null,
    val assigneeId: Int,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: String? = null,
    val subtasks: List<String> = emptyList()
)