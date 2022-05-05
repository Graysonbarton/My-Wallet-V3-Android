package com.blockchain.nabu.metadata

import org.amshove.kluent.`should be equal to`
import org.junit.Test

class NabuUserCredentialsMetadataTest {

    @Test
    fun `should be valid`() {
        NabuUserCredentialsMetadata("userId", "lifeTimeToken", null, null).isValid() `should be equal to` true
    }

    @Test
    fun `empty id, should not be valid`() {
        NabuUserCredentialsMetadata("", "lifeTimeToken", null, null).isValid() `should be equal to` false
    }

    @Test
    fun `empty token, should not be valid`() {
        NabuUserCredentialsMetadata("userId", "", null, null).isValid() `should be equal to` false
    }
}