package com.example.id.util

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.id.R
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.viewmodel.MainViewModel
import com.example.id.viewmodel.WorkdayReportItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Helper extension function to get a Date object at midnight
fun Date.toMidnight(): Date {
    val calendar = Calendar.getInstance()
    calendar.time = this
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.time
}

class PdfExporter(private val context: Context) {

    private data class WorkdayPdfData(
        val headers: List<String>,
        val dataPages: List<List<Pair<List<String>, Int>>>,
        val totalOvertime: Long,
        val totalDrivenKm: Long
    )

    private val germanContext: Context
    private val a4Width = 842 // Landscape A4 width
    private val a4Height = 595 // Landscape A4 height
    private val margin = 40f
    private val textSize = 8f
    private val headerTextSize = 10f
    private val lineSpacing = 12f
    private val rowPadding = 4f // Padding within the row

    init {
        val config = context.resources.configuration
        val locale = Locale.GERMAN
        config.setLocale(locale)
        germanContext = context.createConfigurationContext(config)
    }

    fun exportAndSendReportToWhatsApp(
        reportTypeKey: String,
        results: List<Any>,
        viewModel: MainViewModel,
        userName: String
    ) {
        if (results.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        val document = PdfDocument()
        val title = when (reportTypeKey) {
            "work_time" -> "${germanContext.getString(R.string.work_time_report)} - $userName"
            "refueling" -> "${germanContext.getString(R.string.refuel_report)} - $userName"
            "loading" -> "${germanContext.getString(R.string.loading_report)} - $userName"
            else -> "Bericht"
        }

        try {
            when (reportTypeKey) {
                "work_time" -> {
                    val workdayData = prepareWorkdayData(results.filterIsInstance<WorkdayReportItem>(), viewModel)
                    if (workdayData.headers.isNotEmpty()) {
                        drawWorkdayTable(document, title, workdayData, reportTypeKey, viewModel)
                    }
                }
                "refueling" -> {
                    val (headers, data) = prepareRefuelData(results.filterIsInstance<RefuelEvent>())
                    if (headers.isNotEmpty()) {
                        drawGenericTable(document, title, headers, data, reportTypeKey)
                    }
                }
                "loading" -> {
                    val (headers, data) = prepareLoadingData(results.filterIsInstance<LoadingEvent>(), viewModel)
                    if (headers.isNotEmpty()) {
                        drawGenericTable(document, title, headers, data, reportTypeKey)
                    }
                }
                else -> {}
            }

            val fileName = "Bericht_${reportTypeKey}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            val fileUri = FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", file)

            try {
                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }
                sendPdfToWhatsApp(fileUri, "+491715261942")
            } catch (e: IOException) {
                Log.e("PdfExporter", "Error saving PDF to cache", e)
            }

        } catch (e: Exception) {
            Log.e("PdfExporter", "Error during PDF generation", e)
            Toast.makeText(context, context.getString(R.string.error_during_pdf_generation, e.message), Toast.LENGTH_LONG).show()
        } finally {
            document.close()
        }
    }

    private fun sendPdfToWhatsApp(fileUri: Uri, phoneNumber: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra("jid", "$phoneNumber@s.whatsapp.net")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(sendIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportReportToPdf(
        reportTypeKey: String,
        results: List<Any>,
        viewModel: MainViewModel,
        userName: String
    ) {
        if (results.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        val document = PdfDocument()
        val title = when (reportTypeKey) {
            "work_time" -> "${germanContext.getString(R.string.work_time_report)} - $userName"
            "refueling" -> "${germanContext.getString(R.string.refuel_report)} - $userName"
            "loading" -> "${germanContext.getString(R.string.loading_report)} - $userName"
            else -> "Bericht"
        }

        try {
            when (reportTypeKey) {
                "work_time" -> {
                    val workdayData = prepareWorkdayData(results.filterIsInstance<WorkdayReportItem>(), viewModel)
                    if (workdayData.headers.isNotEmpty()) {
                        drawWorkdayTable(document, title, workdayData, reportTypeKey, viewModel)
                    }
                }
                "refueling" -> {
                    val (headers, data) = prepareRefuelData(results.filterIsInstance<RefuelEvent>())
                    if (headers.isNotEmpty()) {
                        drawGenericTable(document, title, headers, data, reportTypeKey)
                    }
                }
                "loading" -> {
                    val (headers, data) = prepareLoadingData(results.filterIsInstance<LoadingEvent>(), viewModel)
                    if (headers.isNotEmpty()) {
                        drawGenericTable(document, title, headers, data, reportTypeKey)
                    }
                }
                else -> {}
            }
            savePdf(document, reportTypeKey)
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error during PDF generation", e)
            Toast.makeText(context, context.getString(R.string.error_during_pdf_generation, e.message), Toast.LENGTH_LONG).show()
            document.close() // Close document on error
        }
    }

    private fun drawLogo(canvas: Canvas) {
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.pdf_logo_playstore)
        val logoWidth = 100f // Desired width for the logo
        val logoHeight = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * logoWidth
        val logoRect = RectF(margin, margin - 25f, margin + logoWidth, margin - 25f + logoHeight)
        canvas.drawBitmap(logoBitmap, null, logoRect, null)
    }

    private fun drawWorkdayTable(document: PdfDocument, title: String, workdayData: WorkdayPdfData, reportTypeKey: String, viewModel: MainViewModel) {
        val headers = workdayData.headers
        val dataPages = workdayData.dataPages

        if (headers.isEmpty()) {
            Log.e("PdfExporter", "Cannot draw table with empty headers.")
            return
        }

        val colWidths = headers.mapIndexed { index, _ ->
            getColumnWidth(headers.size, index, reportTypeKey)
        }

        dataPages.forEachIndexed { pageIndex, pageData ->
            val page = document.startPage(createPageInfo(pageIndex + 1))
            val canvas = page.canvas
            var yPosition = margin

            // Draw logo and main title only on the first page
            if (pageIndex == 0) {
                drawLogo(canvas)
                val titlePaint = TextPaint().apply {
                    textSize = 16f
                    isFakeBoldText = true
                }
                // Center the title vertically next to the logo
                val logoHeight = 60f // Approximate logo height
                val titleY = margin - 25f + logoHeight / 2 - (titlePaint.descent() + titlePaint.ascent()) / 2
                canvas.drawText(title, margin + 120f, titleY, titlePaint)

                // Draw overtime and total km in the top right corner
                val infoPaint = TextPaint().apply {
                    textSize = headerTextSize
                    isFakeBoldText = true
                    color = Color.BLACK
                    textAlign = Paint.Align.RIGHT
                }
                val overtimeText = "Überstunden gesamt: ${viewModel.formatDuration(workdayData.totalOvertime)}"
                canvas.drawText(overtimeText, a4Width - margin, margin, infoPaint)

                val totalKmText = "Gefahrene km gesamt: ${workdayData.totalDrivenKm} km"
                canvas.drawText(totalKmText, a4Width - margin, margin + lineSpacing + 5f, infoPaint)

                yPosition += logoHeight + lineSpacing - 15f
            } else {
                // For subsequent pages, just add some top margin
                yPosition += margin
            }

            // Draw headers on every page
            yPosition = drawHeaders(canvas, headers, yPosition, colWidths)

            // Draw data rows for the current page
            pageData.forEach { (row, dayOfWeek) ->
                if (row.size == 1 && row[0] == "SEPARATOR") {
                    yPosition += 5f // Add some space before the line
                    canvas.drawLine(margin, yPosition, a4Width - margin, yPosition, Paint().apply {
                        color = Color.BLACK
                        strokeWidth = 1f
                    })
                    yPosition += 5f // Add some space after the line
                } else {
                    val rowHeight = calculateRowHeight(row)
                    val backgroundPaint = Paint()
                    var shouldDrawBg = true
                    when (dayOfWeek) {
                        Calendar.SATURDAY -> backgroundPaint.color = Color.YELLOW
                        Calendar.SUNDAY -> backgroundPaint.color = Color.rgb(255, 192, 203) // Pink
                        else -> shouldDrawBg = false
                    }

                    if (shouldDrawBg) {
                        canvas.drawRect(margin, yPosition, a4Width - margin, yPosition + rowHeight, backgroundPaint)
                    }
                    yPosition = drawRow(canvas, row, yPosition, colWidths)
                }
            }
            document.finishPage(page)
        }
    }

    private fun drawGenericTable(document: PdfDocument, title: String, headers: List<String>, data: List<List<String>>, reportTypeKey: String) {
        if (headers.isEmpty()) {
            Log.e("PdfExporter", "Cannot draw table with empty headers.")
            return
        }
        var pageNumber = 1
        var page: PdfDocument.Page = document.startPage(createPageInfo(pageNumber))
        var canvas = page.canvas
        var yPosition = margin

        // Draw logo
        drawLogo(canvas)

        // Draw main title, centered vertically next to the logo
        val titlePaint = TextPaint().apply {
            textSize = 16f
            isFakeBoldText = true
        }
        val logoHeight = 60f // Approximate logo height
        val titleY = margin - 25f + logoHeight / 2 - (titlePaint.descent() + titlePaint.ascent()) / 2
        canvas.drawText(title, margin + 120f, titleY, titlePaint)
        yPosition += logoHeight + lineSpacing - 15f

        val colWidths = headers.mapIndexed { index, _ ->
            getColumnWidth(headers.size, index, reportTypeKey)
        }

        // Draw headers
        yPosition = drawHeaders(canvas, headers, yPosition, colWidths)

        // Draw data rows
        data.forEach { row ->
            val rowHeight = calculateRowHeight(row)
            if (yPosition + rowHeight > a4Height - margin) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(createPageInfo(pageNumber))
                canvas = page.canvas
                yPosition = margin
                // Redraw logo and headers on new page
                drawLogo(canvas)
                yPosition = drawHeaders(canvas, headers, margin + logoHeight + lineSpacing, colWidths)
            }
            yPosition = drawRow(canvas, row, yPosition, colWidths)
        }

        document.finishPage(page)
    }

    private fun createPageInfo(pageNumber: Int): PdfDocument.PageInfo {
        return PdfDocument.PageInfo.Builder(a4Width, a4Height, pageNumber).create()
    }

    private fun drawHeaders(canvas: Canvas, headers: List<String>, y: Float, colWidths: List<Float>): Float {
        val headerPaint = TextPaint().apply {
            textSize = headerTextSize
            isFakeBoldText = true
            color = Color.BLACK
        }
        canvas.drawRect(margin, y, a4Width - margin, y + (lineSpacing * 1.5f), Paint().apply { color = Color.LTGRAY })
        var x = margin + 5f
        headers.forEachIndexed { index, header ->
            canvas.drawText(header, x, y + lineSpacing, headerPaint)
            x += colWidths.getOrElse(index) { 0f }
        }
        return y + (lineSpacing * 1.5f)
    }

    private fun drawRow(canvas: Canvas, rowData: List<String>, y: Float, colWidths: List<Float>): Float {
        val rowPaint = TextPaint().apply {
            textSize = textSize
            color = Color.BLACK
        }
        val rowHeight = calculateRowHeight(rowData)
        var x = margin + 5f

        rowData.forEachIndexed { index, cellData ->
            val lines = cellData.lines()
            var lineY = y + rowPadding + textSize // Adjusted for padding and text size
            lines.forEach { line ->
                canvas.drawText(line, x, lineY, rowPaint)
                lineY += lineSpacing
            }
            x += colWidths.getOrElse(index) { 0f }
        }

        canvas.drawLine(margin, y + rowHeight, a4Width - margin, y + rowHeight, Paint().apply { color = Color.GRAY })
        return y + rowHeight
    }

    private fun calculateRowHeight(rowData: List<String>): Float {
        val maxLines = rowData.maxOfOrNull { it.lines().size } ?: 1
        return (maxLines * lineSpacing) + rowPadding * 2
    }

    private fun getColumnWidth(columnCount: Int, index: Int, reportTypeKey: String): Float {
        val totalWidth = a4Width - 2 * margin
        return when (reportTypeKey) {
            "work_time" -> when (index) {
                0 -> totalWidth * 0.10f // Datum
                1 -> totalWidth * 0.12f // Kennzeichen
                2 -> totalWidth * 0.15f // Arbeitsbeginn
                3 -> totalWidth * 0.15f // Arbeitsende
                4 -> totalWidth * 0.08f // Pause
                5 -> totalWidth * 0.08f // Anfang-km
                6 -> totalWidth * 0.08f // End-km
                7 -> totalWidth * 0.08f // Gef. km
                8 -> totalWidth * 0.1f  // Nettozeit
                else -> 0f
            }
            "refueling" -> when (index) {
                0 -> totalWidth * 0.10f // Datum
                1 -> totalWidth * 0.10f // Uhrzeit
                2 -> totalWidth * 0.30f // Adresse
                3 -> totalWidth * 0.10f // km-Stand
                4 -> totalWidth * 0.10f // Menge (L)
                5 -> totalWidth * 0.15f // Treibstoff
                6 -> totalWidth * 0.15f // Kennzeichen
                else -> 0f
            }
            "loading" -> when (index) {
                0 -> totalWidth * 0.08f // Lade-Nr.
                1 -> totalWidth * 0.12f // Datum
                2 -> totalWidth * 0.35f // Adresse
                3 -> totalWidth * 0.25f // Von-Bis
                4 -> totalWidth * 0.20f // Ladezeit (new)
                else -> 0f
            }
            else -> totalWidth / columnCount
        }
    }

    private fun formatCity(fullAddress: String?): String {
        if (fullAddress == null) return ""
        val parts = fullAddress.split(",")
        return if (parts.size > 1) parts[1].trim().split(" ").drop(1).joinToString(" ") else parts.firstOrNull() ?: ""
    }

    private fun formatAddressNoCountry(fullAddress: String?): String {
        if (fullAddress == null) return ""
        return fullAddress.substringBeforeLast(',')
    }

    private fun prepareWorkdayData(items: List<WorkdayReportItem>, viewModel: MainViewModel): WorkdayPdfData {
        val headers = listOf(
            germanContext.getString(R.string.pdf_header_date),
            germanContext.getString(R.string.pdf_header_license_plate),
            germanContext.getString(R.string.pdf_header_work_start),
            germanContext.getString(R.string.pdf_header_work_end),
            germanContext.getString(R.string.pdf_header_break),
            germanContext.getString(R.string.pdf_header_start_km),
            germanContext.getString(R.string.pdf_header_end_km),
            germanContext.getString(R.string.pdf_header_driven_km),
            germanContext.getString(R.string.pdf_header_net_time)
        )
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        val allPagesData = mutableListOf<List<Pair<List<String>, Int>>>()
        var currentPageRows = mutableListOf<Pair<List<String>, Int>>()
        var daysInCurrentPage = 0

        // Calculate overtime and total driven km
        val workItems = items.filter { it.workday.type == EventType.WORK }
        val totalNetWorkDuration = workItems.sumOf { it.netWorkDuration }
        val uniqueWorkDays = workItems.map { it.workday.startTime.toMidnight() }.distinct().count()
        val totalExpectedWorkDuration = uniqueWorkDays * 8 * 3600 * 1000L
        val totalOvertime = totalNetWorkDuration - totalExpectedWorkDuration
        val totalDrivenKm = workItems.sumOf { item ->
            val start = item.workday.startOdometer ?: 0
            val end = item.workday.endOdometer ?: 0
            if (start > 0 && end > 0 && end > start) (end - start).toLong() else 0L
        }

        // Calculate date range
        val calendar = Calendar.getInstance(Locale.GERMAN) // Use German locale for week start
        val (minDate, maxDate) = if (items.isEmpty()) {
            calendar.time = Date() // Today
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val min = calendar.time.toMidnight()
            calendar.add(Calendar.DATE, 13) // Two weeks from Monday
            val max = calendar.time.toMidnight()
            min to max
        } else {
            val earliestEventDate = items.minOf { it.workday.startTime.toMidnight() }
            val latestEventDate = items.maxOf { it.workday.endTime?.toMidnight() ?: it.workday.startTime.toMidnight() }

            calendar.time = earliestEventDate
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val min = calendar.time.toMidnight()

            calendar.time = latestEventDate
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            val max = calendar.time.toMidnight()
            min to max
        }

        // Pre-process items into a map for easier lookup
        val dailyItems = mutableMapOf<Date, MutableList<WorkdayReportItem>>()
        items.forEach { item ->
            when (item.workday.type) {
                EventType.WORK -> {
                    val date = item.workday.startTime.toMidnight()
                    dailyItems.getOrPut(date) { mutableListOf() }.add(item)
                }
                EventType.VACATION, EventType.SICK_LEAVE -> {
                    val startCal = Calendar.getInstance(Locale.GERMAN).apply { time = item.workday.startDate ?: item.workday.startTime }
                    val endCal = Calendar.getInstance(Locale.GERMAN).apply { time = item.workday.endDate ?: item.workday.startTime }
                    while (!startCal.time.after(endCal.time)) {
                        val date = startCal.time.toMidnight()
                        dailyItems.getOrPut(date) { mutableListOf() }.add(item)
                        startCal.add(Calendar.DATE, 1)
                    }
                }
                else -> {}
            }
        }

        val currentCalendar = Calendar.getInstance(Locale.GERMAN).apply { time = minDate }
        while (!currentCalendar.time.after(maxDate)) {
            val currentDate = currentCalendar.time.toMidnight()
            val dayOfWeek = currentCalendar.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek == Calendar.MONDAY && currentDate != minDate.toMidnight()) {
                currentPageRows.add(Pair(listOf("SEPARATOR"), -1))
            }

            val itemsForDay = dailyItems[currentDate] ?: emptyList()

            val workEvents = itemsForDay.filter { it.workday.type == EventType.WORK }
            val vacationSickLeaveEvents = itemsForDay.filter { it.workday.type == EventType.VACATION || it.workday.type == EventType.SICK_LEAVE }

            if (workEvents.isNotEmpty()) {
                workEvents.forEach { item ->
                    val startLocation = formatCity(item.workday.startLocation)
                    val workStart = "${timeFormat.format(item.workday.startTime)} $startLocation"

                    val workEnd = item.workday.endTime?.let {
                        val endLocation = formatCity(item.workday.endLocation)
                        "${timeFormat.format(it)} $endLocation"
                    } ?: germanContext.getString(R.string.running)

                    currentPageRows.add(Pair(listOf(
                        dateFormat.format(item.workday.startTime),
                        item.workday.carPlate ?: "-",
                        workStart,
                        workEnd,
                        viewModel.formatDuration(item.totalBreakDuration),
                        item.workday.startOdometer?.toString() ?: "-",
                        item.workday.endOdometer?.toString() ?: "-",
                        if (item.workday.startOdometer != null && item.workday.endOdometer != null) (item.workday.endOdometer - item.workday.startOdometer).toString() else "-",
                        viewModel.formatDuration(item.netWorkDuration)
                    ), dayOfWeek))
                }
            } else if (vacationSickLeaveEvents.isNotEmpty()) {
                val item = vacationSickLeaveEvents.first()
                currentPageRows.add(Pair(listOf(
                    dateFormat.format(currentDate),
                    if (item.workday.type == EventType.VACATION) germanContext.getString(R.string.vacation) else germanContext.getString(R.string.sick_leave),
                    "-", "-", "-", "-", "-", "-", "-"
                ), dayOfWeek))
            }
            else {
                // Empty day
                currentPageRows.add(Pair(listOf(
                    dateFormat.format(currentDate),
                    "-", "-", "-", "-", "-", "-", "-", "-"
                ), dayOfWeek))
            }

            daysInCurrentPage++
            if (daysInCurrentPage == 14) {
                allPagesData.add(currentPageRows)
                currentPageRows = mutableListOf()
                daysInCurrentPage = 0
            }
            currentCalendar.add(Calendar.DATE, 1)
        }

        if (currentPageRows.isNotEmpty()) {
            // Fill the last page with empty rows if it's not a full two weeks
            val remainingDays = 14 - (currentPageRows.size % 14)
            if (remainingDays in 1..13) { // Only add if not already a full block and not empty
                repeat(remainingDays) {
                    val dayOfWeek = currentCalendar.get(Calendar.DAY_OF_WEEK)
                    currentPageRows.add(Pair(listOf(
                        dateFormat.format(currentCalendar.time.toMidnight()), // Use the next date for empty rows
                        "-", "-", "-", "-", "-", "-", "-", "-"
                    ), dayOfWeek))
                    currentCalendar.add(Calendar.DATE, 1)
                }
            }
            allPagesData.add(currentPageRows)
        }

        return WorkdayPdfData(headers, allPagesData, totalOvertime, totalDrivenKm)
    }

    private fun prepareRefuelData(items: List<RefuelEvent>): Pair<List<String>, List<List<String>>> {
        val headers = listOf(
            germanContext.getString(R.string.pdf_header_date),
            germanContext.getString(R.string.pdf_header_time),
            germanContext.getString(R.string.pdf_header_address),
            germanContext.getString(R.string.pdf_header_odometer),
            germanContext.getString(R.string.pdf_header_amount_l),
            germanContext.getString(R.string.pdf_header_fuel_type),
            germanContext.getString(R.string.pdf_header_license_plate)
        )
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        val data = items.sortedBy { it.timestamp }.map { event ->
            listOf(
                dateFormat.format(event.timestamp),
                timeFormat.format(event.timestamp),
                formatAddressNoCountry(event.location),
                event.odometer.toString(),
                String.format(Locale.GERMAN, "%.2f", event.fuelAmount),
                event.fuelType,
                event.carPlate
            )
        }
        return Pair(headers, data)
    }

    private fun prepareLoadingData(items: List<LoadingEvent>, viewModel: MainViewModel): Pair<List<String>, List<List<String>>> {
        val headers = listOf(
            germanContext.getString(R.string.pdf_header_loading_nr),
            germanContext.getString(R.string.pdf_header_date),
            germanContext.getString(R.string.pdf_header_address),
            germanContext.getString(R.string.pdf_header_from_to),
            germanContext.getString(R.string.pdf_header_loading_duration) // New header
        )
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        val data = items.sortedBy { it.startTime }.mapIndexed { index, event ->
            val duration = if (event.startTime != null && event.endTime != null) {
                event.endTime.time - event.startTime.time
            } else {
                null
            }
            val formattedEndTime = event.endTime?.let { e -> timeFormat.format(e) } ?: "..."
            listOf(
                (index + 1).toString(),
                event.startTime?.let { dateFormat.format(it) } ?: "-",
                formatAddressNoCountry(event.location),
                event.startTime?.let { "${timeFormat.format(it)} - $formattedEndTime" } ?: "-",
                duration?.let { viewModel.formatDuration(it) } ?: "-" // New data field
            )
        }
        return Pair(headers, data)
    }

    private fun savePdf(document: PdfDocument, reportType: String) {
        val fileName = "Bericht_${reportType}_${System.currentTimeMillis()}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        document.writeTo(outputStream)
                    }
                    Toast.makeText(context, context.getString(R.string.pdf_saved_to_downloads), Toast.LENGTH_LONG).show()
                } catch (e: IOException) {
                    Log.e("PdfExporter", "Error saving PDF", e)
                    Toast.makeText(context, context.getString(R.string.error_saving_pdf, e.message), Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("PdfExporter", "Error creating MediaStore URI.")
                Toast.makeText(context, context.getString(R.string.error_creating_pdf_file), Toast.LENGTH_LONG).show()
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)
            try {
                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }
                Toast.makeText(context, context.getString(R.string.pdf_saved_to_downloads), Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                Log.e("PdfExporter", "Error saving PDF", e)
                Toast.makeText(context, context.getString(R.string.error_saving_pdf, e.message), Toast.LENGTH_LONG).show()
            }
        }
        document.close()
    }
}
