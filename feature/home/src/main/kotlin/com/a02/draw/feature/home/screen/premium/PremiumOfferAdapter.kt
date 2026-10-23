package com.a02.draw.feature.home.screen.premium

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.a02.draw.core.ui.billing.PremiumOffer
import com.a02.draw.core.ui.billing.PremiumPricingPhase
import com.a02.draw.core.ui.billing.PremiumProductType
import com.a02.draw.core.ui.billing.PremiumRecurrenceMode
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.databinding.ItemPremiumOfferBinding

internal data class PremiumOfferRow(
    val offer: PremiumOffer,
    val selected: Boolean,
    val popular: Boolean,
)

internal class PremiumOfferAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<PremiumOfferRow, PremiumOfferAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPremiumOfferBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: ItemPremiumOfferBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PremiumOfferRow) = with(binding) {
            val offer = row.offer
            val recurringPhase = offer.pricingPhases.lastOrNull {
                it.recurrenceMode == PremiumRecurrenceMode.INFINITE
            } ?: offer.pricingPhases.lastOrNull()
            val trialPhase = offer.pricingPhases.firstOrNull { it.isFree }
            title.text = offer.title
            description.text = trialPhase?.billingPeriod?.let { period ->
                root.context.getString(
                    R.string.premium_free_trial_phase,
                    period.toDisplayPeriod(root.context),
                )
            } ?: offer.description
            price.text = offer.primaryFormattedPrice
            phases.text = if (offer.productType == PremiumProductType.LIFETIME) {
                root.context.getString(R.string.premium_lifetime)
            } else {
                recurringPhase?.billingPeriod?.let { period ->
                    root.context.getString(
                        R.string.premium_per_period,
                        period.toDisplayPeriod(root.context),
                    )
                }.orEmpty()
            }
            popularBadge.isVisible = row.popular
            radioButton.isChecked = row.selected
            offerCard.strokeWidth = root.resources.getDimensionPixelSize(
                if (row.selected) R.dimen.premium_selected_stroke else R.dimen.premium_default_stroke,
            )
            offerCard.strokeColor = ContextCompat.getColor(
                root.context,
                if (row.selected) R.color.home_purple else R.color.home_border,
            )
            offerCard.setCardBackgroundColor(
                ContextCompat.getColor(
                    root.context,
                    if (row.selected) R.color.premium_card_selected else R.color.premium_card,
                ),
            )
            offerCard.isChecked = row.selected
            offerCard.contentDescription = listOfNotNull(
                offer.title,
                description.text?.toString(),
                offer.primaryFormattedPrice,
                phases.text?.toString(),
            ).joinToString(separator = ", ")
            offerCard.setOnClickListener { onClick(offer.key) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PremiumOfferRow>() {
        override fun areItemsTheSame(oldItem: PremiumOfferRow, newItem: PremiumOfferRow) =
            oldItem.offer.key == newItem.offer.key

        override fun areContentsTheSame(oldItem: PremiumOfferRow, newItem: PremiumOfferRow) =
            oldItem == newItem
    }

}

internal fun PremiumOffer.checkoutButtonLabel(context: Context): String = when {
    productType == PremiumProductType.LIFETIME ->
        context.getString(R.string.premium_buy_lifetime)

    pricingPhases.any(PremiumPricingPhase::isFree) ->
        context.getString(R.string.premium_start_free_trial)

    else -> context.getString(R.string.premium_subscribe)
}

internal fun PremiumOffer.policyDisclosure(context: Context): String {
    if (productType == PremiumProductType.LIFETIME) {
        return context.getString(R.string.premium_lifetime_disclosure, primaryFormattedPrice)
    }

    val hasOfferPhase = pricingPhases.any {
        it.isFree || it.recurrenceMode == PremiumRecurrenceMode.FINITE
    }
    val clauses = pricingPhases.mapNotNull { phase ->
        val rawPeriod = phase.billingPeriod ?: return@mapNotNull null
        val period = rawPeriod.toDisplayPeriod(context)
        when {
            phase.isFree -> context.getString(
                R.string.premium_policy_free_phase,
                rawPeriod.toDisplayPeriod(
                    context = context,
                    multiplier = phase.billingCycleCount.coerceAtLeast(1),
                ),
            )

            phase.recurrenceMode == PremiumRecurrenceMode.FINITE -> context.getString(
                R.string.premium_policy_intro_phase,
                phase.formattedPrice,
                period,
                rawPeriod.toDisplayPeriod(
                    context = context,
                    multiplier = phase.billingCycleCount.coerceAtLeast(1),
                ),
            )

            phase.recurrenceMode == PremiumRecurrenceMode.INFINITE -> context.getString(
                if (hasOfferPhase) {
                    R.string.premium_policy_recurring_after
                } else {
                    R.string.premium_recurring_phase
                },
                phase.formattedPrice,
                period,
            )

            else -> null
        }
    }.toMutableList()

    if (pricingPhases.any(PremiumPricingPhase::isFree)) {
        clauses += context.getString(R.string.premium_trial_cancel_notice)
    }
    return clauses.joinToString(separator = " ").ifBlank {
        context.getString(R.string.premium_terms_confirmed_in_play)
    }
}

internal fun String.toDisplayPeriod(context: Context, multiplier: Int = 1): String {
    val match = SINGLE_PERIOD.matchEntire(this) ?: return removePrefix("P").lowercase()
    val amount = match.groupValues[1].toIntOrNull() ?: return removePrefix("P").lowercase()
    val unit = when (match.groupValues[2]) {
        "D" -> MeasureUnit.DAY
        "W" -> MeasureUnit.WEEK
        "M" -> MeasureUnit.MONTH
        "Y" -> MeasureUnit.YEAR
        else -> return removePrefix("P").lowercase()
    }
    return MeasureFormat.getInstance(
        context.resources.configuration.locales[0],
        MeasureFormat.FormatWidth.WIDE,
    ).format(Measure(amount * multiplier.coerceAtLeast(1), unit))
}

private val SINGLE_PERIOD = Regex("P(\\d+)([DWMY])")

internal fun PremiumOffer.recurringPeriodWeight(): Long {
    val period = pricingPhases.lastOrNull {
        it.recurrenceMode == PremiumRecurrenceMode.INFINITE
    }?.billingPeriod ?: return 0L
    return Regex("(\\d+)([YMWD])").findAll(period).sumOf { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: 0L
        when (match.groupValues[2]) {
            "Y" -> amount * 365L
            "M" -> amount * 30L
            "W" -> amount * 7L
            else -> amount
        }
    }
}
