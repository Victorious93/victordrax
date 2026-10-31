package com.victorious.victordrax

import com.victorious.victordrax.afos.Afos
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AfosTest {
    private val yaml = File("src/main/assets/afos_packages.yaml").readText()

    @Test fun bundledRepositoryParses() {
        val pkgs = Afos.parseRepository(yaml)
        assertEquals(269, pkgs.size)
        assertTrue(pkgs.all { Afos.isSafePackage(it.name) })
        assertTrue(Afos.categoriesOf(pkgs).isNotEmpty())
        assertTrue(pkgs.count { it.categories.isNotEmpty() } > 200)
    }
    @Test fun commands() {
        assertEquals("afos -i nmap", Afos.installCommand("nmap"))
        assertEquals("afos -a", Afos.updateAllCommand()); assertEquals("afos -l", Afos.listInstalledCommand())
        assertThrows(IllegalArgumentException::class.java) { Afos.installCommand("nmap; reboot") }
    }
    @Test fun parseListOutput() {
        val e = "\u001B[0m"
        val out = "[ \u001B[37mnmap$e ] [ \u001B[31m7.94$e ] [ desc ] [ \u001B[34mscanner$e ]\n" +
            "[ \u001B[37msqlmap$e ] [ 1.8 ] [ desc ] [ web ] \u001B[37m(\u001B[0m \u001B[31m==\u001B[0m \u001B[35mNOT INSTALLED\u001B[0m \u001B[31m==\u001B[0m \u001B[37m)\u001B[0m\n"
        assertEquals(setOf("nmap"), Afos.parseInstalled(out))
        val merged = Afos.merge(Afos.parseRepository(yaml), setOf(Afos.parseRepository(yaml).first().name))
        assertEquals(1, merged.count { it.installed })
        assertEquals(1, Afos.filter(merged, "", null, installedOnly = true).size)
    }
}
