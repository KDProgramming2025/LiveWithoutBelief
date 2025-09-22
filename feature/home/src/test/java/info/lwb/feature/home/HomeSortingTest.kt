/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.home

import info.lwb.core.model.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSortingTest {
    @Test
    fun `menu items are sorted by order then title`() {
        val items = listOf(
            MenuItem(id = "3", title = "Zeta", order = 2),
            MenuItem(id = "1", title = "Alpha", order = 1),
            MenuItem(id = "2", title = "Beta", order = 2)
        )
        val sorted = items.sortedWith(compareBy({ it.order }, { it.title }))
        assertEquals(listOf("1", "2", "3"), sorted.map { it.id })
    }
}
