// "Remember me" toggles where the auth token lives: localStorage survives
// browser restarts, sessionStorage clears when the tab closes.
const KEY = 'token'

export function getToken() {
  return localStorage.getItem(KEY) || sessionStorage.getItem(KEY)
}

export function setToken(token, remember = true) {
  if (remember) {
    localStorage.setItem(KEY, token)
    sessionStorage.removeItem(KEY)
  } else {
    sessionStorage.setItem(KEY, token)
    localStorage.removeItem(KEY)
  }
}

export function clearToken() {
  localStorage.removeItem(KEY)
  sessionStorage.removeItem(KEY)
}
