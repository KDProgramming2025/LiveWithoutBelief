/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.articles

/**
 * Filter describing which article collection should be displayed.
 * - [All] shows the full reactive article list.
 * - [Label] shows a snapshot list filtered by a (case-insensitive) label match.
 */
sealed interface ArticlesFilter {
    /** Full catalog of locally cached articles (reactive). */
    data object All : ArticlesFilter

    /** Articles whose label equals / contains [value] (case-insensitive). */
    data class Label(val value: String) : ArticlesFilter
}
