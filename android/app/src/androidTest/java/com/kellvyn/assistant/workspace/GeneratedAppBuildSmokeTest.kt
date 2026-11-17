package com.kellvyn.assistant.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end smoke test for the real on-device generated-project pipeline.
 *
 * It feeds deterministic model-shaped output into LocalProjectBuilder, then proves
 * that the local ECJ -> D8 -> binary manifest -> APK signing path produces an APK.
 * The CI workflow installs that APK and taps its button on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedAppBuildSmokeTest {

    @Test
    fun generatedNativeJavaProjectBuildsAndExportsSignedApkForDeviceTest() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)

        // The CI runner accepts the Android SDK terms before this test is executed.
        // This mirrors the explicit user-consent gate in the product without weakening it.
        LocalAndroidCompiler(context, workspace).acceptSdkLicense()

        val preferences = context.getSharedPreferences("kellvyn_native", Context.MODE_PRIVATE)
        preferences.edit().remove("local_builder_last_apk_path").commit()

        val prompt = "Crie um aplicativo Android nativo e gere o APK chamado \"Kellvyn Smoke\"."
        val response = listOf(
            fileBlock("settings.gradle.kts", "kotlin", """
                pluginManagement {
                    repositories { google(); mavenCentral(); gradlePluginPortal() }
                }
                dependencyResolutionManagement {
                    repositories { google(); mavenCentral() }
                }
                rootProject.name = "KellvynSmoke"
                include(":app")
            """),
            fileBlock("build.gradle.kts", "kotlin", """
                plugins {
                    id("com.android.application") version "8.7.3" apply false
                }
            """),
            fileBlock("gradle.properties", "properties", """
                org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
                org.gradle.parallel=false
                org.gradle.workers.max=2
            """),
            fileBlock("app/build.gradle.kts", "kotlin", """
                plugins { id("com.android.application") }
                android {
                    namespace = "com.example.app"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "com.example.app"
                        minSdk = 26
                        targetSdk = 35
                    }
                }
            """),
            fileBlock("app/src/main/AndroidManifest.xml", "xml", """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="Kellvyn Smoke Test">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """),
            fileBlock("app/src/main/java/com/example/app/MainActivity.java", "java", """
                package com.example.app;

                import android.app.Activity;
                import android.os.Bundle;
                import android.view.ViewGroup;
                import android.widget.Button;
                import android.widget.LinearLayout;
                import android.widget.TextView;

                public class MainActivity extends Activity {
                    private int count = 0;
                    private TextView counter;

                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);

                        LinearLayout root = new LinearLayout(this);
                        root.setOrientation(LinearLayout.VERTICAL);
                        root.setPadding(32, 48, 32, 32);

                        TextView title = new TextView(this);
                        title.setText("APP_BUILD_SMOKE_OK");
                        root.addView(title, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        counter = new TextView(this);
                        counter.setText("Contagem: 0");
                        root.addView(counter);

                        Button increment = new Button(this);
                        increment.setText("Incrementar");
                        increment.setOnClickListener(view -> {
                            count++;
                            counter.setText("Contagem: " + count);
                        });
                        root.addView(increment);

                        setContentView(root);
                    }
                }
            """),
            fileBlock("README.md", "markdown", """
                Aplicativo nativo mínimo para validar o construtor local do Kellvyn.
                A tela mostra um marcador de inicialização e um botão que incrementa
                um contador, permitindo verificar compilação, instalação, abertura e
                interação real na interface Android.
            """)
        ).joinToString("\n\n")

        val completion = CountDownLatch(1)
        val bridgeResult = AtomicReference<String>("")
        val bridge = LocalProjectBuilderBridge(
            context,
            object : LocalProjectBuilderBridge.Listener {
                override fun onProgress(message: String) = Unit
                override fun onComplete(message: String) {
                    bridgeResult.set(message)
                    completion.countDown()
                }
            }
        )
        try {
            assertTrue("A ponte do chat não reconheceu o pedido nativo de APK.",
                bridge.isNativeApkRequest(prompt))
            assertTrue("A ponte não anexou o contrato restrito do compilador.",
                bridge.addCompilerContract(prompt).contains("COMPILADOR LOCAL DE APK ANDROID"))

            // Exercise the same Kotlin bridge that MainActivity now calls after model output.
            bridge.compileGeneratedProject(prompt, response)
            assertTrue(
                "A ponte do chat não concluiu a compilação dentro do limite do teste.",
                completion.await(8, TimeUnit.MINUTES)
            )
        } finally {
            bridge.close()
        }
        val result = bridgeResult.get()

        assertTrue(
            "A ponte do chat não confirmou uma compilação local, assinatura e verificação reais. Resultado: " + result,
            result.contains("APK Android nativo compilado localmente, assinado e verificado.")
        )

        val apkPath = preferences.getString("local_builder_last_apk_path", null)
        assertTrue("O construtor não registrou o caminho do APK produzido.", !apkPath.isNullOrBlank())

        val builtApk = File(requireNotNull(apkPath))
        assertTrue("O APK gerado não existe ou está vazio.", builtApk.isFile && builtApk.length() > 20_000L)

        val artifactDirectory = File(context.filesDir, "kellvyn-lab")
        assertTrue("Não foi possível preparar a pasta de artefatos de teste.",
            artifactDirectory.mkdirs() || artifactDirectory.isDirectory)
        builtApk.copyTo(File(artifactDirectory, "generated-app.apk"), overwrite = true)
        }
    }

    private fun fileBlock(path: String, language: String, source: String): String {
        val fence = String(charArrayOf(96, 96, 96))
        return "Arquivo: " + path + "\n" + fence + language + "\n" +
            source.trimIndent() + "\n" + fence
    }
}
