-- TetraVim Action Lock (shared busy-guard for refactor.lua / extract.lua)
--
-- refactor.lua's project-wide rename and extract.lua's extract/inline
-- actions both drive the same buffer-local quickfix-preview-then-apply
-- flow against the same JDTLS/Kotlin LS client. Each module used to keep
-- its own private `M._busy` flag, which only guarded against a SECOND
-- action of the SAME kind overlapping -- a rename in progress in
-- refactor.lua would NOT block a concurrent extract in extract.lua (or vice
-- versa), letting two overlapping previews/applies race against the same
-- buffers. This module is the single shared source of truth for that guard
-- instead, without changing either module's public API.

local M = {}

local busy = false

--- @return boolean true while any refactor/extract action is in flight.
function M.is_busy()
  return busy
end

--- Marks the lock held. Callers must pair this with a later release() on
--- EVERY path out (success, warning, error, cancel) or the lock stays
--- stuck and every future action is rejected.
function M.acquire()
  busy = true
end

--- Releases the lock.
function M.release()
  busy = false
end

return M
