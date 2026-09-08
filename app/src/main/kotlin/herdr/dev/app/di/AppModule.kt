package herdr.dev.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.HerdrSocketRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindHerdrSocketRepository(
        impl: HerdrSocketRepositoryImpl,
    ): HerdrSocketRepository
}
