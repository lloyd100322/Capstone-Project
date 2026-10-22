package com.mobile.enerjeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the password hashing used in place of the old plaintext
 * password field: the same password with the same salt always gives the
 * same hash, the salt is random per user, and a wrong password never
 * verifies.
 */
class PasswordHasherTest {

    @Test
    fun theSamePasswordWithTheSameSaltGivesTheSameHash() {
        val salted = PasswordHasher.hash("secret-password")

        val again = PasswordHasher.hashWithSalt("secret-password", salted.salt)

        assertEquals(salted.hash, again)
    }

    @Test
    fun everyPasswordGetsItsOwnRandomSalt() {
        val first = PasswordHasher.hash("same-password")
        val second = PasswordHasher.hash("same-password")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
    }

    @Test
    fun theHashIsNotThePassword() {
        val salted = PasswordHasher.hash("secret-password")

        assertFalse(salted.hash.contains("secret"))
        assertFalse(salted.salt.contains("secret"))
    }

    @Test
    fun theCorrectPasswordVerifies() {
        val salted = PasswordHasher.hash("AngBaitNaPassword")

        assertTrue(
            PasswordHasher.verify(
                "AngBaitNaPassword",
                salted.salt,
                salted.hash
            )
        )
    }

    @Test
    fun aWrongPasswordDoesNotVerify() {
        val salted = PasswordHasher.hash("AngBaitNaPassword")

        assertFalse(
            PasswordHasher.verify(
                "MalingPassword",
                salted.salt,
                salted.hash
            )
        )

        assertFalse(
            PasswordHasher.verify(
                "",
                salted.salt,
                salted.hash
            )
        )
    }

    @Test
    fun anEmptyStoredHashNeverVerifies() {
        assertFalse(PasswordHasher.verify("anything", "", ""))
        assertFalse(PasswordHasher.verify("anything", "abcd", ""))
    }
}
