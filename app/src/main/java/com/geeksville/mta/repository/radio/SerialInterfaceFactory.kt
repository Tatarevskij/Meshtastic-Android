package com.geeksville.mta.repository.radio

import dagger.assisted.AssistedFactory

/**
 * Factory for creating `SerialInterface` instances.
 */
@AssistedFactory
interface SerialInterfaceFactory : InterfaceFactorySpi<SerialInterface>