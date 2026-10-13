package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class ContactNameMatcherTest {
    private fun entry(id: Long, name: String, number: String = "+50370000001") =
        PhoneContactCandidate(id, TripCallContact(name, number))

    @Test fun exactNamesTakePriorityAndAccentsAreNormalized() {
        val exact = entry(1, "Ána")
        assertEquals(exact.contact, ContactNameMatcher.findUnique("ana", listOf(exact, entry(2, "Ana López"))))
        assertEquals("José López", ContactNameMatcher.findUnique("jose", listOf(entry(3, "José López"))).name)
    }
    @Test fun ambiguousPeopleAreNeverChosenEvenIfTheirPhoneMatches() {
        assertThrows(IllegalArgumentException::class.java) {
            ContactNameMatcher.findUnique("ana", listOf(entry(1, "Ana López"), entry(2, "Ana Pérez")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContactNameMatcher.findUnique("ana", listOf(entry(1, "Ana"), entry(2, "Ana")))
        }
    }
    @Test fun duplicatesAreCollapsedButMultipleNumbersAreRejected() {
        val single = entry(1, "Ana")
        assertEquals(single.contact, ContactNameMatcher.findUnique("ana", listOf(single, single)))
        assertThrows(IllegalArgumentException::class.java) {
            ContactNameMatcher.findUnique("ana", listOf(single, entry(1, "Ana", "+50370000002")))
        }
    }
    @Test fun partialWordsAndMissingContactsDoNotMatch() {
        listOf("an", "maria", "a").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { ContactNameMatcher.findUnique(name, listOf(entry(1, "Ana López"))) }
        }
    }

    @Test fun ambiguousNamesExplainTheOptionsWithoutRevealingPhoneNumbers() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ContactNameMatcher.findUnique("ana", listOf(entry(1, "Ana López"), entry(2, "Ana Pérez")))
        }
        assertTrue(failure.message!!.contains("Ana López"))
        assertTrue(failure.message!!.contains("Ana Pérez"))
        assertFalse(failure.message!!.contains("70000001"))
        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            ContactNameMatcher.findUnique("ana", listOf(entry(1, "Ana"), entry(2, "Ana")))
        }
        assertTrue(duplicate.message!!.contains("nombres distintos"))
    }
}

