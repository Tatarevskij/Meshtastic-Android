package com.geeksville.mta.repository.radio

import dagger.assisted.AssistedFactory

/**
 * Factory for creating `BluetoothInterface` instances.
 */
@AssistedFactory
interface BluetoothInterfaceFactory : InterfaceFactorySpi<BluetoothInterface>