package com.geeksville.mta.repository.radio

/**
 * Address identifiers for all supported radio backend implementations.
 */
enum class InterfaceId(val id: Char) {
    BLUETOOTH('x'),
    MOCK('m'),
    NOP('n'),
    SERIAL('s'),
    TCP('t'),
    ;

    companion object {
        fun forIdChar(id: Char): InterfaceId? {
            return entries.firstOrNull { it.id == id }
        }
    }
}