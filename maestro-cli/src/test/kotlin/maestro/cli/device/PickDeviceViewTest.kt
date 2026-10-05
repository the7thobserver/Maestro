package maestro.cli.device

import com.google.common.truth.Truth.assertThat
import maestro.cli.CliError
import maestro.device.Device
import maestro.device.DeviceSpec
import maestro.device.Platform
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.stream.SystemIn
import uk.org.webcompere.systemstubs.stream.SystemOut
import uk.org.webcompere.systemstubs.stream.input.LinesAltStream

@ExtendWith(SystemStubsExtension::class)
class PickDeviceViewTest {

    @SystemStub
    private val systemIn = SystemIn()

    @SystemStub
    private val systemOut = SystemOut()

    @Test
    fun `pickRunningDevice prints a flat list and returns the chosen device`() {
        systemIn.lines("2")

        val picked = PickDeviceView.pickRunningDevice(sampleDevices())

        assertThat(picked.description).isEqualTo(IPHONE)
        assertThat(visibleOutput()).isEqualTo(
            """
            There are multiple connected devices
            [1]: emulator-5554
            [2]: $IPHONE
            Please choose one (or "q" to quit): 
            """.trimIndent()
        )
    }

    @Test
    fun `pickRunningDevice numbers devices in the order they were given`() {
        val firstAndroid = connected("emulator-5554", Platform.ANDROID)
        val ios = connected("iPhone 15", Platform.IOS)
        val secondAndroid = connected("emulator-5556", Platform.ANDROID)
        systemIn.lines("2")

        val picked = PickDeviceView.pickRunningDevice(listOf(firstAndroid, ios, secondAndroid))

        assertThat(picked).isEqualTo(ios)
    }

    @Test
    fun `pickRunningDevice accepts a padded number after rejecting invalid input`() {
        systemIn.lines("nope", "0", "9", "   ", " 1 ")

        val picked = PickDeviceView.pickRunningDevice(sampleDevices())

        assertThat(picked.description).isEqualTo("emulator-5554")
        val output = visibleOutput()
        assertThat(output).contains("""Please enter a number between 1 and 2, or "q" to quit.""")
        assertThat(Regex("""Please choose one \(or "q" to quit\): """).findAll(output).count()).isEqualTo(5)
    }

    @Test
    fun `pickRunningDevice treats q as cancel`() {
        for (input in listOf("q", "Q", " q ")) {
            systemOut.clear()
            systemIn.lines(input)

            val error = assertThrows<CliError> {
                PickDeviceView.pickRunningDevice(sampleDevices())
            }

            assertThat(error.message).isEqualTo("Device selection was cancelled")
        }
    }

    @Test
    fun `pickRunningDevice treats end of input as cancel`() {
        systemIn.lines()

        val error = assertThrows<CliError> {
            PickDeviceView.pickRunningDevice(sampleDevices())
        }

        assertThat(error.message).isEqualTo("Device selection was cancelled")
    }

    private fun SystemIn.lines(vararg lines: String) {
        setInputStream(LinesAltStream(*lines))
    }

    private fun visibleOutput(): String {
        return ANSI.replace(systemOut.text, "").trimEnd('\r', '\n')
    }

    private fun sampleDevices(): List<Device> {
        return listOf(
            connected("emulator-5554", Platform.ANDROID),
            connected(IPHONE, Platform.IOS),
        )
    }

    private fun connected(description: String, platform: Platform): Device.Connected {
        return Device.Connected(
            instanceId = description,
            description = description,
            platform = platform,
            deviceType = when (platform) {
                Platform.IOS -> Device.DeviceType.SIMULATOR
                Platform.WEB -> Device.DeviceType.BROWSER
                Platform.ANDROID -> Device.DeviceType.EMULATOR
            },
            deviceSpec = when (platform) {
                Platform.ANDROID -> DeviceSpec.Android.DEFAULT
                Platform.IOS -> DeviceSpec.Ios.DEFAULT
                Platform.WEB -> DeviceSpec.Web.DEFAULT
            },
        )
    }

    private companion object {
        const val IPHONE = "iPhone 15 Pro - iOS 17.5 - 276BC887-488A-452A-B4CC-CD6DF899EF45"
        val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    }
}
