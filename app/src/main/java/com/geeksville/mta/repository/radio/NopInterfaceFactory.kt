package com.geeksville.mta.repository.radio

import dagger.assisted.AssistedFactory

/**
 * Factory for creating `NopInterface` instances.
 */
@AssistedFactory
interface NopInterfaceFactory : InterfaceFactorySpi<NopInterface>