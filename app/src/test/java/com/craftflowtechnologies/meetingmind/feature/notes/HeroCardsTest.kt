package com.craftflowtechnologies.meetingmind.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroCardsTest {
    private fun tile(action: TileAction) = MeetingTile(action, action.name, "sub")

    @Test fun actionItemsAndSummaryAreThePreferredTwo() {
        val tiles = listOf(tile(TileAction.KEY_POINTS), tile(TileAction.ACTION_ITEMS), tile(TileAction.SUMMARY), tile(TileAction.ASSISTANT))
        assertEquals(listOf(TileAction.ACTION_ITEMS, TileAction.SUMMARY), heroCards(tiles).map { it.action })
    }

    @Test fun keyPointsFillTheSecondCardWhenThereIsNoSummary() {
        val tiles = listOf(tile(TileAction.KEY_POINTS), tile(TileAction.ACTION_ITEMS))
        assertEquals(listOf(TileAction.ACTION_ITEMS, TileAction.KEY_POINTS), heroCards(tiles).map { it.action })
    }

    @Test fun noTilesMeansNoCards() = assertEquals(emptyList<MeetingTile>(), heroCards(emptyList()))
}
