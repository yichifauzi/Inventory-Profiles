/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2019-2020 jsnimda <7615255+jsnimda@users.noreply.github.com>
 *   Copyright (c) 2021-2022 Plamen K. Kosseff <p.kosseff@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.anti_ad.mc.ipnext.item

import org.anti_ad.mc.alias.component.ComponentChanges
import org.anti_ad.mc.alias.component.ComponentMap
import org.anti_ad.mc.alias.component.ComponentMapImpl
import org.anti_ad.mc.alias.component.ComponentType
import org.anti_ad.mc.alias.component.DataComponentTypes
import org.anti_ad.mc.alias.item.Item
import org.anti_ad.mc.alias.item.Items
import org.anti_ad.mc.ipnext.item.ComponentUtils.`(withRemovedIf)`

import org.anti_ad.mc.ipnext.item.rule.CountSink
import org.anti_ad.mc.ipnext.item.rule.CountSource
import java.util.function.*

// different nbt is treated as different type, as they can't stack together
data class ItemType(val item: Item,
                    private val aTag: ComponentMapImpl?,
                    private val aChanges: ComponentChanges?,
                    val isDamageableFn: (() -> Boolean),
                    var ignoreDurability: Boolean = false,
                    private val isDamageable: Boolean = isDamageableFn(),
                    private var stackSource: (ItemType) -> ItemStack? = { null }): CountSink<ItemType> {


    private val EMPTY_COMPONENT_MAP = ComponentMapImpl(ComponentMap.EMPTY)

    val sourceStack: ItemStack? = stackSource(this)

    val tag: ComponentMapImpl?

    val changes: ComponentChanges

    val accumulatedCount: Int
        get() {
            return countSource.count(this)
        }


    init {
        //if (null == aTag) Log.trace("aTag = NULL here", Exception())
        if (aTag == null && item != Items.AIR) {
            tag = EMPTY_COMPONENT_MAP
            changes = ComponentChanges.EMPTY
        } else {
            tag = aTag
            changes = aChanges ?: ComponentChanges.EMPTY
        }
    }

    override fun toString() = item.toString() + "" + (tag ?: "")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItemType

        if (isEmpty() && other.isEmpty()) return true
        if (item != other.item) return false
        if (!ignoreDurability || !isDamageable) {
            if (changes != other.changes) return false
        } else {
            val removePredicate: Predicate<ComponentType<*>> = Predicate { type ->
                type == DataComponentTypes.DAMAGE
            }
            val ourLocalChanges = this.changes.`(withRemovedIf)`(removePredicate)
            val otherLocalChanges = other.changes.`(withRemovedIf)`(removePredicate)
            return ourLocalChanges == otherLocalChanges
        }
        return true
    }
    private fun tagHashCode(): Int {
        var result = 0
        if (!ignoreDurability || !isDamageable) {
            result = tag?.hashCode() ?: 0
        }
        tag?.let {
            //todo make stable hashCode from components

        }
        return result
    }

    override fun hashCode(): Int {
        if (isEmpty()) return 0 // temp solution for StackOverflowError
        var result = item.hashCode()
        result = 31 * result + tagHashCode()
        return result
    }

    companion object {

    }

    private var countSource: CountSource<ItemType> = CountSource<ItemType> { _ -> 0 }

    override fun setCountSource(source: CountSource<ItemType>) {
        countSource = source
    }
}
