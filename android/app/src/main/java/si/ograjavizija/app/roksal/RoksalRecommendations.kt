package si.ograjavizija.app.roksal

import si.ograjavizija.app.data.RoksalConfig
import si.ograjavizija.app.data.RoksalOrientation
import si.ograjavizija.app.data.RoksalPrivacy

data class RoksalRecommendation(
    val title: String,
    val profileId: String,
    val gapMm: Int,
    val privacy: RoksalPrivacy,
    val reason: String,
    val note: String,
)

object RoksalRecommendations {
    fun suggest(config: RoksalConfig): List<RoksalRecommendation> {
        if (config.category.name == "TERASA") return emptyList()
        val candidates = if (config.orientation == RoksalOrientation.POKONCNA) {
            listOf(
                RoksalRecommendation(
                    "Čista zasebna ograja", "P128", 3, RoksalPrivacy.ZASEBNA,
                    "Širša polna deska ustvari bolj zaprt videz z malo fugami.",
                    "Dobra začetna konfiguracija za več zasebnosti."
                ),
                RoksalRecommendation(
                    "Sodoben zračen videz", "ROMB67", 18, RoksalPrivacy.SREDNJA,
                    "ROMB 67 ustvari izrazitejši vzorec in bolj zračen videz.",
                    "Pri ROMB je alu cev v sredini obvezna."
                ),
                RoksalRecommendation(
                    "Klasična ozka fuga", "P100", 8, RoksalPrivacy.SREDNJA,
                    "Polna deska 100 je preprost pokončni sistem z vidnim vijačenjem.",
                    "Končni razmak stebrov in konstrukcijo mora potrditi Roksal."
                )
            )
        } else {
            listOf(
                RoksalRecommendation(
                    "Zaprta vodoravna", "P128", 5, RoksalPrivacy.ZASEBNA,
                    "Široka vodoravna deska daje miren, enoten videz.",
                    "Preveri razmak stebrov za izbrano izvedbo."
                ),
                RoksalRecommendation(
                    "Vodoravni ROMB", "ROMB67", 15, RoksalPrivacy.SREDNJA,
                    "ROMB ustvari poudarjen horizontalni vzorec.",
                    "Pri prečni izvedbi so potrebni ustrezni kotniki in alu jedro."
                ),
                RoksalRecommendation(
                    "Široka terasa deska", "DESKA150", 8, RoksalPrivacy.SREDNJA,
                    "DESKA 150 daje zelo malo horizontalnih linij in izrazit videz.",
                    "Izberi površino KLASIK ali RUSTIK."
                )
            )
        }
        return candidates
    }
}
