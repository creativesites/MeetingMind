package com.craftflowtechnologies.meetingmind.core.originals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalsParserTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("originals/$name")!!.readText().lines()

    @Test fun readsGreekWordsOfJohn15v5() {
        val words = res("tagnt_excerpt.txt").mapNotNull(OriginalsParser::greek).filter { it.book == "JHN" }
        assertTrue(words.size >= 20)
        assertEquals((1..words.size).toList(), words.map { it.position })
        val vine = words.first { it.position == 4 }
        assertEquals("ἄμπελος,", vine.surface)
        assertEquals("ampelos", vine.translit)
        assertEquals("G0288", vine.strongs)
        assertEquals("N-NSF", vine.morph)
        assertEquals("ἄμπελος", vine.lemma)
        assertEquals("vine,", vine.gloss)
        assertEquals(15, vine.chapter); assertEquals(5, vine.verse)
    }

    @Test fun twoWordsJoinedByOneGreekTokenKeepBothStrongs() {
        val kagō = res("tagnt_excerpt.txt").mapNotNull(OriginalsParser::greek).first { it.position == 12 }
        assertEquals(listOf("G1473", "G2532"), kagō.strongsAll)
        assertEquals("P-1NS + CONJ", kagō.morph)
    }

    @Test fun readsHebrewGenesis1v1WithPrefixes() {
        val words = res("tahot_excerpt.txt").mapNotNull(OriginalsParser::hebrew)
        assertEquals(7, words.size)
        val first = words.first()
        assertTrue(first.hebrew)
        assertEquals("GEN", first.book)
        assertEquals("be./re.Shit", first.translit)
        assertEquals("in beginning", first.gloss)
        assertTrue(first.strongsAll.containsAll(listOf("H9003", "H7225G")))
    }

    @Test fun readsLexiconEntriesAndStripsMarkup() {
        val entries = res("lexicon_excerpt.txt").mapNotNull(OriginalsParser::lexicon).associateBy { it.strongs }
        val love = entries.getValue("G0026")
        assertEquals("ἀγάπη", love.lemma)
        assertEquals("agapē", love.translit)
        assertEquals("love", love.gloss)
        assertTrue(!love.meaning.contains("<") && !love.meaning.contains("<ref"))
        assertTrue(love.meaning.length <= 701)
        assertEquals("God", entries.getValue("H0430G").gloss)
    }

    @Test fun headerAndLicenceLinesAreIgnored() {
        assertNull(OriginalsParser.greek("TAGNT Mat-Jhn - Translators Amalgamated Greek NT"))
        assertNull(OriginalsParser.greek("Mat.1.1#01=NKO\tx\ty\tG1=N\tz\tNA27"))  // not in the NA28 text
        assertNull(OriginalsParser.hebrew("Gen.1.1#01=Q(K)\ta\tb\tc\td\te"))
        assertNull(OriginalsParser.lexicon("=====\tx"))
        assertNotNull(OriginalsParser.BOOKS["Jhn"])
        assertEquals(66, OriginalsParser.BOOKS.size)
    }

    @Test fun decodesGreekGrammarByRule() {
        assertEquals("noun · nominative · singular · feminine", MorphDecoder.greek("N-NSF"))
        assertEquals("verb · present · active · participle · nominative · singular · masculine", MorphDecoder.greek("V-PAP-NSM"))
        assertEquals("verb · present · active · indicative · 1st person · singular", MorphDecoder.greek("V-PAI-1S"))
        assertEquals("personal pronoun · 1st person · nominative · singular", MorphDecoder.greek("P-1NS"))
        assertEquals("noun · genitive · singular · masculine · proper name", MorphDecoder.greek("N-GSM-P"))
        assertEquals("preposition", MorphDecoder.greek("PREP"))
        assertEquals("personal pronoun · 1st person · nominative · singular  |  conjunction", MorphDecoder.greek("P-1NS + CONJ"))
    }

    @Test fun decodesHebrewGrammarByRule() {
        assertEquals("noun · masculine · plural · absolute", MorphDecoder.hebrew("HNcmpa"))
        assertEquals("verb · Qal · perfect · 3rd person · masculine · singular", MorphDecoder.hebrew("HVqp3ms"))
        assertEquals("preposition  +  noun · feminine · singular · absolute", MorphDecoder.hebrew("HR/Ncfsa"))
        assertEquals("object marker", MorphDecoder.hebrew("HTo"))
    }
}
