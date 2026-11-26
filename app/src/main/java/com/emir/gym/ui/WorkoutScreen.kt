package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.data.ActivityType
import com.emir.gym.data.DayStatus
import com.emir.gym.data.PlanDayType
import com.emir.gym.data.PlanLogic
import com.emir.gym.data.WeeklyStats
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.DayItem
import com.emir.gym.ui.components.DaySelector
import com.emir.gym.ui.components.DayState
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.PrimaryButton
import com.emir.gym.ui.components.ProgressRingCard
import com.emir.gym.ui.components.SecondaryButton
import com.emir.gym.ui.components.SectionHeader
import com.emir.gym.ui.components.StatTile
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.upperTr
import java.time.LocalDate
import kotlin.math.roundToLong

/** Antrenman sekmesi: hafta özeti, bugünün kartı ve gün klasörlerine giriş. */
@Composable
fun WorkoutScreen(vm: GymViewModel, onOpenDay: (Int) -> Unit, modifier: Modifier = Modifier) {
    val entries by vm.entries.collectAsState()
    val days by vm.planDays.collectAsState()
    val exercises by vm.planExercises.collectAsState()
    val activities by vm.activities.collectAsState()

    val list = entries
    if (list == null) {
        ScreenColumn(modifier) { Text(stringResource(R.string.screens_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val today = LocalDate.now()
    val week = remember(today, days, list, activities) { PlanLogic.weekProgress(today, today, days, list, activities) }
    val summary = remember(list, week.weekStart) { WeeklyStats.summarize(list, week.weekStart) }
    val minutes = remember(week.weekStart, activities) { FolderLogic.activityMinutes(week.weekStart, activities) }
    val short = stringArrayResource(R.array.ui_weekday_short)
    val items = week.days.mapIndexed { i, d ->
        DayItem(
            label = short[i],
            state = when (d.status) {
                DayStatus.TAMAMLANDI -> DayState.TAMAMLANDI
                DayStatus.PLANLI -> DayState.PLANLI
                DayStatus.DINLENME -> DayState.DINLENME
                DayStatus.KACIRILDI -> DayState.KACIRILDI
            },
            isToday = d.date == today,
        )
    }
    val todayPlan = PlanLogic.planFor(today, days, exercises)
    val todayStatus = week.days[today.dayOfWeek.value - 1].status

    ScreenColumn(modifier) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            dateWithWeekday(today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        DaySelector(items, selectedIndex = today.dayOfWeek.value - 1, onSelect = { onOpenDay(it + 1) })
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressRingCard(
                done = week.doneWeightDays,
                total = week.plannedWeightDays,
                label = stringResource(R.string.screens_this_week),
                caption = stringResource(R.string.screens_workouts_caption),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = UiFormat.grouped(summary.totalVolume.roundToLong()),
                unit = stringResource(R.string.screens_kg),
                label = stringResource(R.string.screens_weekly_tonnage),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        TodayCard(
            weekday = today.dayOfWeek.value,
            plan = todayPlan,
            status = todayStatus,
            onOpen = { onOpenDay(today.dayOfWeek.value) },
        )

        SectionHeader(stringResource(R.string.screens_activity_week))
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityType.entries.forEach { t ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            activityLabel(t).upperTr(),
                            style = LabelStyles.caps,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.screens_minutes, UiFormat.grouped((minutes[t] ?: 0).toLong())),
                            style = NumberStyles.small,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    weekday: Int,
    plan: com.emir.gym.data.DayPlan?,
    status: DayStatus,
    onOpen: () -> Unit,
) {
    val c = GymTheme.colors
    val type = plan?.day?.type ?: PlanDayType.DINLENME
    AppCard(Modifier.fillMaxWidth(), highlighted = type != PlanDayType.DINLENME && status != DayStatus.TAMAMLANDI) {
        Text(
            stringResource(R.string.screens_today_caps, weekdayName(weekday)).upperTr(),
            style = LabelStyles.caps,
            color = c.accent,
        )
        Spacer(Modifier.height(6.dp))
        when (type) {
            PlanDayType.AGIRLIK -> {
                val exs = plan?.exercises.orEmpty()
                Text(plan?.day?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                val minutes = FolderLogic.estimateMinutes(plan?.day, exs)
                val sets = exs.sumOf { it.sets }
                val parts = buildList {
                    if (minutes != null) add(stringResource(R.string.screens_approx_minutes, minutes))
                    add(pluralStringResource(R.plurals.screens_exercise_count, exs.size, exs.size))
                    add(stringResource(R.string.screens_set_count, sets))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (exs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        exs.joinToString(" · ") { com.emir.gym.data.ExerciseCatalog.displayName(it.name) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (status == DayStatus.TAMAMLANDI) {
                    Text(stringResource(R.string.screens_today_done), style = MaterialTheme.typography.titleSmall, color = c.accent)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    stringResource(if (status == DayStatus.TAMAMLANDI) R.string.screens_open_folder else R.string.screens_start_workout),
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                    icon = GymIcons.Play,
                )
            }
            PlanDayType.YUZME -> {
                Text(plan?.day?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                val note = plan?.day?.note.orEmpty()
                if (note.isNotBlank()) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                if (status == DayStatus.TAMAMLANDI) {
                    Text(stringResource(R.string.screens_swim_done), style = MaterialTheme.typography.titleSmall, color = c.accent)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(stringResource(R.string.screens_log_swim), onClick = onOpen, modifier = Modifier.fillMaxWidth(), icon = GymIcons.Plus)
            }
            PlanDayType.DINLENME -> {
                Text(
                    plan?.day?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.screens_rest_day),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    plan?.day?.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.screens_rest_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(stringResource(R.string.screens_rest_add_sauna), onClick = onOpen, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
