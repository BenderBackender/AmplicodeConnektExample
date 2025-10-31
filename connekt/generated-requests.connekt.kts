import org.assertj.core.api.Assertions


POST("http://localhost:8080/api/auth/signup") {
    header("Content-Type", "application/json")
    body(
        """
        {
            "firstName": "Alex",
            "lastName": "Pushkin",
            "username": "AlexPushkin",
            "email": "apushkin@habr.ru",
            "password": "password"
        }
        """.trimIndent()
    )

}

val email: String by env
val password: String by env

val token by POST("http://localhost:8080/api/auth/signin") {
    header("Content-Type", "application/json")
    body(
        """
        {
            "usernameOrEmail": "$email",
            "password": "$password"
        }
        """.trimIndent()
    )
} then {
    jsonPath().readString("$.accessToken")
}

GET("http://localhost:8080/api/users/me") {
    bearerAuth(token)
}

val postTitle: String by env
val postBody: String by env

val categoryId by POST("http://localhost:8080/api/categories") {
    header("Content-Type", "application/json")
    bearerAuth(token)
    body(
        """
        {
            "name": "Books"
        }
        """.trimIndent()
    )

} then {
    jsonPath().readInt("$.id")
}

class PostResponse {
    val title: String? = null
    val body: String? = null
    val category: String? = null
    var tags: MutableList<String?>? = null
}
POST("http://localhost:8080/api/posts") {
    header("Content-Type", "application/json")
    bearerAuth(token)
    body(
        """
        {
            "title": "$postTitle Глава ${Random.nextInt()}",
            "body": "$postBody",
            "categoryId": $categoryId,
            "tags": ["Пушкин"]
        }
        """.trimIndent()
    )
} then {
    Assertions.assertThat(code).isEqualTo(201)
    jsonPath().doRead<PostResponse>("$").also { post ->
        Assertions.assertThat(post.category).isEqualTo("Books")
        Assertions.assertThat(post.tags).contains("Пушкин")
    }
}

POST("http://localhost:8080/api/posts") {
    header("Content-Type", "application/json")
    bearerAuth(token)
    body(
        """
        {
            "title": "$postTitle Глава ${Random.nextInt(0, 50)}",
            "body": "$postBody",
            "categoryId": $categoryId,
            "tags": ["Пушкин"]
        }
        """.trimIndent()
    )
} then {
    Assertions.assertThat(code).isEqualTo(201)
    jsonPath().doRead<String>("$.category").also { Assertions.assertThat(it).isEqualTo("Books") }
    jsonPath().doRead<List<String>>("$.tags").also { Assertions.assertThat(it).contains("Пушкин") }
}


useCase("Поиск по тегу и категории") {

    data class Tag(
        val name: String
    )

    data class Category(
        val name: String,
        val id: Long
    )

    data class Post(
        val tags: List<Tag>,
        val category: Category,
        val title: String
    )


    val token by POST("http://localhost:8080/api/auth/signin") {
        header("Content-Type", "application/json")
        body(
            """
        {
            "usernameOrEmail": "$email",
            "password": "$password"
        }
        """.trimIndent()
        )
    } then {
        jsonPath().readString("$.accessToken")
    }

    fun addCategory(category: String): Category {
        return POST("http://localhost:8080/api/categories") {
            header("Content-Type", "application/json")
            bearerAuth(token)
            body(
                """
        {
            "name": "$category"
        }
        """.trimIndent()
            )
        } then {
            Assertions.assertThat(code).isEqualTo(201)
            jsonPath().doRead<Category>("$")
        }
    }

    fun findByTagAndName(categoryName: String, tagName: String): List<Post> {
        return GET("http://localhost:8080/api/posts") {
            bearerAuth(token)
            queryParam("page", "0")
            queryParam("size", "10")
        } then {
            jsonPath().doRead<List<Post>>("$.content")
                .filter {
                    it.tags.any { tag -> tag.name == tagName } && it.category.name == categoryName
                }
        }
    }

    fun getAllCategory(): List<Category> {
        return GET("http://localhost:8080/api/categories") {
            bearerAuth(token)
            queryParam("page", "0")
            queryParam("size", "10")
        } then {
            jsonPath().doRead<List<Category>>("$.content")
        }
    }

    fun findCategory(category: String) = getAllCategory().firstOrNull { it.name == category }

    fun findPostByCategory(category: String): List<Post>? {
        return findCategory(category)?.let {
            GET("http://localhost:8080/api/posts/category/{id}") {
                bearerAuth(token)
                pathParam("id", it.id)
                queryParam("page", "0")
                queryParam("size", "10")
            } then {
                jsonPath().doRead<List<Post>>("$.content")
            }
        }

    }

    fun addPost(postTitle: String, postBody: String, categoryName: String, tag: String) {
        val category = findCategory(categoryName) ?: addCategory(categoryName)
        POST("http://localhost:8080/api/posts") {
            header("Content-Type", "application/json")
            bearerAuth(token)
            body(
                """
        {
            "title": "$postTitle Глава}",
            "body": "$postBody",
            "categoryId": ${category.id},
            "tags": ["$tag"]
        }
        """.trimIndent()
            )
        } then {
            Assertions.assertThat(code).isEqualTo(201)
        }
    }

    addCategory("It")
    addCategory("Business")
    addCategory("Life-style")


    addPost("Сергей Александрович Есенин", postBody, "Стихи", "Поэзия")
    addPost("Как начать свой бизнес", postBody, "Business", "История")
    addPost("История первых ЭВМ", postBody, "It", "История")
    addPost("Unix системы", postBody, "It", "Unix")

    val allCategory = getAllCategory()

    val resultByCategory = findPostByCategory("It")

    val resultByBooks = findByTagAndName("Стихи", "Поэзия")

    val resultByIt = findByTagAndName("It", "История")

    println("Все категории : ")
    allCategory.forEach { category -> println(category.name) }
    println("Поиск по тегу It : ")
    resultByCategory?.forEach { post -> println(post.tags.map { it.name } + post.title) }
    println("Поиск по категории Стихи и тегу Поэзия  : ")
    resultByBooks.forEach { post -> println(post.title) }
    println("Поиск по категории It и тегу История  : ")
    resultByIt.forEach { post -> println(post.title) }
}