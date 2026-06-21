import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

export function getStadiums() {
  return api.get('/stadiums')
}

export function getSlots(date, type) {
  return api.get('/slots', { params: { date, type } })
}

export function getSlot(slotId) {
  return api.get(`/slots/${slotId}`)
}

export function reserve(userId, slotId) {
  return api.post('/reservation', { userId, slotId })
}

export function cancelReservation(reservationId, userId) {
  return api.post(`/reservation/cancel/${reservationId}`, null, { params: { userId } })
}

export function getReservations(userId) {
  return api.get('/reservations', { params: { userId } })
}

export function getReservationUsers() {
  return api.get('/reservation-users')
}

export function warmUpStock(date) {
  return api.post('/slots/warmup', null, { params: { date } })
}