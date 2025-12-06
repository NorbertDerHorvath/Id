package com.example.id.data

import com.example.id.data.dao.BreakEventDao
import com.example.id.data.dao.LoadingEventDao
import com.example.id.data.dao.RefuelEventDao
import com.example.id.data.dao.WorkdayEventDao
import com.example.id.data.entities.BreakEvent
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.network.ApiService
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

class AppRepository @Inject constructor(
    private val database: AppDatabase,
    private val apiService: ApiService
) {

    private val workdayEventDao = database.workdayEventDao()
    private val breakEventDao = database.breakEventDao()
    private val refuelEventDao = database.refuelEventDao()
    private val loadingEventDao = database.loadingEventDao()

    // Workday Events
    suspend fun insertWorkdayEvent(event: WorkdayEvent): Long {
        return workdayEventDao.insertWorkdayEvent(event)
    }

    suspend fun updateWorkdayEvent(event: WorkdayEvent) {
        workdayEventDao.updateWorkdayEvent(event)
    }

    suspend fun replaceWorkdayEvent(oldId: Long, newEvent: WorkdayEvent) {
        workdayEventDao.replaceWorkdayEvent(oldId, newEvent)
    }

    // Refuel Events
    suspend fun insertRefuelEvent(event: RefuelEvent): Long {
        return refuelEventDao.insertRefuelEvent(event)
    }

    suspend fun updateRefuelEvent(event: RefuelEvent) {
        refuelEventDao.updateRefuelEvent(event)
    }

    suspend fun replaceRefuelEvent(oldId: Long, newEvent: RefuelEvent) {
        refuelEventDao.replaceRefuelEvent(oldId, newEvent)
    }

    // Loading Events
    suspend fun insertLoadingEvent(event: LoadingEvent): Long {
        return loadingEventDao.insertLoadingEvent(event)
    }

    suspend fun updateLoadingEvent(event: LoadingEvent) {
        loadingEventDao.updateLoadingEvent(event)
    }

    suspend fun replaceLoadingEvent(oldId: Long, newEvent: LoadingEvent) {
        loadingEventDao.replaceLoadingEvent(oldId, newEvent)
    }

    // --- The rest of the repository remains the same ---

    fun getAllWorkdayEvents(userId: String): Flow<List<WorkdayEvent>> {
        return workdayEventDao.getAllWorkdayEvents(userId)
    }

    suspend fun getWorkdayEventById(id: Long): WorkdayEvent? {
        return workdayEventDao.getWorkdayEventById(id)
    }

    fun getActiveWorkdayEvent(): Flow<WorkdayEvent?> {
        return workdayEventDao.getActiveWorkdayEvent()
    }

    fun getActiveWorkdayEvent(userId: String): Flow<WorkdayEvent?> {
        return workdayEventDao.getActiveWorkdayEvent(userId)
    }

    fun getWorkdayEventsForReport(userId: String, startDate: Date?, endDate: Date?): Flow<List<WorkdayEvent>> {
        return workdayEventDao.getWorkdayEventsForReport(userId, startDate, endDate)
    }

    suspend fun deleteWorkdayEventById(id: Long) {
        workdayEventDao.deleteWorkdayEventById(id)
    }

    fun getWorkdayEventsByPlate(userId: String, carPlate: String): Flow<List<WorkdayEvent>> {
        return workdayEventDao.getWorkdayEventsByPlate(userId, carPlate)
    }

    suspend fun insertBreakEvent(event: BreakEvent): Long {
        return breakEventDao.insertBreakEvent(event)
    }

    suspend fun updateBreakEvent(event: BreakEvent) {
        breakEventDao.updateBreakEvent(event)
    }

    fun getAllBreakEvents(userId: String): Flow<List<BreakEvent>> {
        return breakEventDao.getAllBreakEvents(userId)
    }

    fun getBreaksForWorkday(workdayEventId: Long): Flow<List<BreakEvent>> {
        return breakEventDao.getBreaksForWorkday(workdayEventId)
    }

    fun getActiveBreakEvent(userId: String): Flow<BreakEvent?> {
        return breakEventDao.getActiveBreakEvent(userId)
    }

    fun getAllRefuelEvents(userId: String): Flow<List<RefuelEvent>> {
        return refuelEventDao.getAllRefuelEvents(userId)
    }

    fun getRefuelEventsByCar(carPlate: String): Flow<List<RefuelEvent>> {
        return refuelEventDao.getRefuelEventsByCar(carPlate)
    }

    fun getRefuelEventsForReport(userId: String, startDate: Date?, endDate: Date?, carPlate: String?, fuelType: String?, paymentMethod: String?): Flow<List<RefuelEvent>> {
        return refuelEventDao.getRefuelEventsForReport(userId, startDate, endDate, carPlate, fuelType, paymentMethod)
    }

    suspend fun getRefuelEventById(id: Long): RefuelEvent? {
        return refuelEventDao.getRefuelEventById(id)
    }

    suspend fun deleteRefuelEventById(id: Long) {
        refuelEventDao.deleteRefuelEventById(id)
    }

    fun getAllLoadingEvents(userId: String): Flow<List<LoadingEvent>> {
        return loadingEventDao.getAllLoadingEvents(userId)
    }

    fun getActiveLoadingEvent(userId: String): Flow<LoadingEvent?> {
        return loadingEventDao.getActiveLoadingEvent(userId)
    }

    fun getLoadingsForWorkday(workdayEventId: Long): Flow<List<LoadingEvent>> {
        return loadingEventDao.getLoadingsForWorkday(workdayEventId)
    }

    fun getLoadingEventsForReport(userId: String, startDate: Date?, endDate: Date?): Flow<List<LoadingEvent>> {
        return loadingEventDao.getLoadingEventsForReport(userId, startDate, endDate)
    }

    suspend fun getLoadingEventById(id: Long): LoadingEvent? {
        return loadingEventDao.getLoadingEventById(id)
    }

    suspend fun deleteLoadingEventById(id: Long) {
        loadingEventDao.deleteLoadingEventById(id)
    }

    // New methods for recent events
    suspend fun getRecentWorkdayEvents(userId: String): List<WorkdayEvent> {
        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        return workdayEventDao.getWorkdayEventsAfter(userId, sevenDaysAgo)
    }

    suspend fun getRecentRefuelEvents(userId: String): List<RefuelEvent> {
        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        return refuelEventDao.getRefuelEventsAfter(userId, sevenDaysAgo)
    }

    suspend fun getRecentLoadingEvents(userId: String): List<LoadingEvent> {
        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        return loadingEventDao.getLoadingEventsAfter(userId, sevenDaysAgo)
    }

    suspend fun deleteWorkdayEvent(id: Long) {
        workdayEventDao.deleteWorkdayEventById(id)
    }

    suspend fun deleteRefuelEvent(id: Long) {
        refuelEventDao.deleteRefuelEventById(id)
    }

    suspend fun deleteLoadingEvent(id: Long) {
        loadingEventDao.deleteLoadingEventById(id)
    }

    // Methods for SyncWorker
    suspend fun getUnsyncedWorkdayEvents(): List<WorkdayEvent> {
        return workdayEventDao.getUnsyncedWorkdayEvents()
    }

    suspend fun getUnsyncedRefuelEvents(): List<RefuelEvent> {
        return refuelEventDao.getUnsyncedRefuelEvents()
    }

    suspend fun getUnsyncedLoadingEvents(): List<LoadingEvent> {
        return loadingEventDao.getUnsyncedLoadingEvents()
    }

    suspend fun syncWorkdayEvents(serverEvents: List<WorkdayEvent>) {
        workdayEventDao.syncWorkdayEvents(serverEvents)
    }

    suspend fun syncRefuelEvents(serverEvents: List<RefuelEvent>) {
        refuelEventDao.syncRefuelEvents(serverEvents)
    }

    suspend fun syncLoadingEvents(serverEvents: List<LoadingEvent>) {
        loadingEventDao.syncLoadingEvents(serverEvents)
    }

    suspend fun deleteAllData() {
        // This should probably be handled more gracefully, e.g. by a specific server endpoint.
        // For now, we'll just clear the local DB.
        workdayEventDao.clearAll()
        refuelEventDao.clearAll()
        loadingEventDao.clearAll()
        breakEventDao.clearAll()
    }
}
