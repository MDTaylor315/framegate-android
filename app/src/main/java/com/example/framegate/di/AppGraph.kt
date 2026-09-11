package com.example.framegate.di

import com.example.framegate.domain.queue.PersistentQueueStore

/**
 * Contenedor de dependencias hecho a mano (DI manual, sin Hilt/Koin/Dagger).
 *
 * Mantiene las instancias de alcance de aplicación que deben compartirse entre
 * pantallas. En particular, [queueStore] es una única instancia compartida para
 * que lo que la pantalla Capture encola sea visible en la pantalla Queue.
 */
object AppGraph {
    val queueStore: PersistentQueueStore by lazy { PersistentQueueStore() }
}
