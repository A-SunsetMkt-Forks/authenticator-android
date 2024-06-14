package com.bitwarden.authenticator.data.platform.manager.imports.parsers

import com.bitwarden.authenticator.data.authenticator.datasource.disk.entity.AuthenticatorItemAlgorithm
import com.bitwarden.authenticator.data.authenticator.datasource.disk.entity.AuthenticatorItemEntity
import com.bitwarden.authenticator.data.authenticator.datasource.disk.entity.AuthenticatorItemType
import com.bitwarden.authenticator.data.platform.manager.imports.model.AegisJsonExport
import com.bitwarden.authenticator.data.platform.manager.imports.model.ExportParseResult
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID

class AegisExportParser : ExportParser {
    @OptIn(ExperimentalSerializationApi::class)
    override fun parse(byteArray: ByteArray): ExportParseResult {
        val importJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        return try {
            val exportData = importJson
                .decodeFromStream<AegisJsonExport>(ByteArrayInputStream(byteArray))
            ExportParseResult.Success(
                items = exportData
                    .db
                    .entries
                    .toAuthenticatorItemEntities(),
            )
        } catch (e: SerializationException) {
            ExportParseResult.Error()
        } catch (e: IllegalArgumentException) {
            ExportParseResult.Error()
        } catch (e: IOException) {
            ExportParseResult.Error()
        }
    }

    private fun List<AegisJsonExport.Database.Entry>.toAuthenticatorItemEntities() =
        map { it.toAuthenticatorItemEntity() }

    private fun AegisJsonExport.Database.Entry.toAuthenticatorItemEntity(): AuthenticatorItemEntity {

        // Aegis only supports TOTP codes.
        val type = AuthenticatorItemType.fromStringOrNull(type)
            ?: throw IllegalArgumentException("Unsupported OTP type")

        val algorithmEnum = AuthenticatorItemAlgorithm
            .fromStringOrNull(info.algo)
            ?: throw IllegalArgumentException("Unsupported algorithm.")

        val issuer = issuer
            .takeUnless { it.isEmpty() }
        // If issuer is not provided we fallback to the account name.
            ?: name
                .split(":")
                .first()
        val accountName = name
            .split(":")
            .last()
            // If the account name matches the derived issuer we ignore it to prevent redundancy.
            .takeUnless { it == issuer }

        return AuthenticatorItemEntity(
            id = UUID.randomUUID().toString(),
            key = info.secret,
            type = type,
            algorithm = algorithmEnum,
            period = info.period,
            digits = info.digits,
            issuer = issuer,
            userId = null,
            accountName = accountName,
            favorite = favorite,
        )
    }
}
