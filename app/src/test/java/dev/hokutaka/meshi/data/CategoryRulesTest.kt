package dev.hokutaka.meshi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryRulesTest {
    private val categories = listOf(
        Category("a", "食費", "expense", 0),
        Category("b", "日用品", "expense", 1),
        Category("c", "交通費", "expense", 2)
    )

    @Test fun trimsCategoryLabel() {
        assertEquals("食費", CategoryRules.normalizeLabel("  食費  "))
    }

    @Test fun rejectsBlankAndOverlongName() {
        assertThrows(IllegalArgumentException::class.java) {
            CategoryRules.normalizeLabel("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryRules.normalizeLabel("あ".repeat(41))
        }
    }

    @Test fun movingCategoryPreservesIds() {
        val moved = CategoryRules.reorder(categories, "b", -1)
        assertEquals(listOf("b", "a", "c"), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.sortOrder })
        assertEquals(categories.map { it.id }.toSet(), moved.map { it.id }.toSet())
    }

    @Test fun movingBeyondBoundsDoesNothing() {
        assertEquals(categories, CategoryRules.reorder(categories, "a", -1))
        assertEquals(categories, CategoryRules.reorder(categories, "c", 1))
    }
}
