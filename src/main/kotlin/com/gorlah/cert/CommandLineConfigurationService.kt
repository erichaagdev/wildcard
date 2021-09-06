package com.gorlah.cert

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

class CommandLineConfigurationService(
    private val arguments: Array<String>
): ConfigurationService {

    override fun load(): Configuration? {
        val mutableArguments = MutableArguments()
        val parser = JCommander.newBuilder()
            .addObject(mutableArguments)
            .build()
        try {
            parser.parse(*arguments)
        } catch (e: Exception) {
            parser.usage()
            return null
        }

        return Configuration(
            bucket = mutableArguments.bucket!!,
            domain = mutableArguments.domain!!,
            email = mutableArguments.email!!,
            location = mutableArguments.location,
            production = mutableArguments.production
        )
    }

    private data class MutableArguments(

        @field:Parameter(names = ["-b", "--bucket"], required = true, description = "Google Cloud Storage bucket")
        var bucket: String? = null,

        @field:Parameter(names = ["-e", "--email"], required = true, description = "Email address of user")
        var email: String? = null,

        @field:Parameter(names = ["-d", "--domain"], required = true, description = "Domain to be certified")
        var domain: String? = null,

        @field:Parameter(names = ["-l", "--location"], required = false, description = "Location for files")
        var location: String? = null,

        @field:Parameter(names = ["--production"], required = false, description = "Generate a production certificate")
        var production: Boolean = false,
    )
}