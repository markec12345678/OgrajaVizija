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
        return when (config.category.name) {
            "FASADA" -> listOf(
                RoksalRecommendation(
                    "Enoten fasadni videz", "P100", 8, RoksalPrivacy.SREDNJA,
                    "Polna deska 100 je primerna za enoten fasadni ritem.",
                    "Roksal mora potrditi podkonstrukcijo."
                ),
                RoksalRecommendation(
                    "Poudarjen relief", "ROMB67", 15, RoksalPrivacy.SREDNJA,
                    "ROMB ustvari bolj poudarjeno teksturo fasade.",
                    "Potrebna je ustrezna podkonstrukcija in alu jedro."
                ),
                RoksalRecommendation(
                    "KUBO arhitektura", "KUBO8042", 10, RoksalPrivacy.ODPRTA,
                    "KUBO omogoča izrazitejši 80/42 mm profil.",
                    "Izbira notranje alu cevi vpliva na konstrukcijo in razpon."
                )
            )
            "NAPUSC" -> listOf(
                RoksalRecommendation(
                    "Enoten napušč", "P100", 5, RoksalPrivacy.SREDNJA,
                    "Polna deska 100 je med aktualnimi profili za napušč.",
                    "Izbiro smeri in podkonstrukcije uskladi z izvedbo."
                ),
                RoksalRecommendation(
                    "Diskreten napušč", "ROMB67", 10, RoksalPrivacy.ODPRTA,
                    "ROMB 67 je dodatna možnost za napušč.",
                    "Potrebna je notranja alu cev in ustrezna podkonstrukcija."
                )
            )
            "STROP" -> listOf(
                RoksalRecommendation(
                    "Čist strop", "P100", 5, RoksalPrivacy.SREDNJA,
                    "Polna deska 100 je ena od aktualnih možnosti za strop.",
                    "Izbiro smeri in podkonstrukcije uskladi z izvedbo."
                ),
                RoksalRecommendation(
                    "Arhitekturni strop", "KUBO8042", 10, RoksalPrivacy.ODPRTA,
                    "KUBO 80/42 je po uradnem obrazcu na voljo za strop.",
                    "Notranja alu rešitev in razpon morata biti tehnično potrjena."
                ),
                RoksalRecommendation(
                    "Reliefni strop", "ROMB67", 10, RoksalPrivacy.ODPRTA,
                    "ROMB 67 je dodatna možnost za strop.",
                    "Potrebna je notranja alu cev in ustrezna podkonstrukcija."
                )
            )
            "PREGRADNA_STENA" -> if (config.orientation == RoksalOrientation.POKONCNA) {
                listOf(
                    RoksalRecommendation(
                        "Zasebna pokončna pregrada", "P128", 3, RoksalPrivacy.ZASEBNA,
                        "Širša polna deska ustvari bolj zaprt videz.",
                        "Preveri razmak nosilcev in stebrov."
                    ),
                    RoksalRecommendation(
                        "Pokončni ROMB", "ROMB67", 15, RoksalPrivacy.SREDNJA,
                        "ROMB ustvari bolj zračen vzorec.",
                        "Alu cev v sredini je obvezna."
                    ),
                    RoksalRecommendation(
                        "KUBO pregrada", "KUBO8042", 10, RoksalPrivacy.ODPRTA,
                        "KUBO omogoča bolj izrazito arhitekturno pregrado.",
                        "Izbira notranje alu cevi vpliva na razpon."
                    )
                )
            } else {
                listOf(
                    RoksalRecommendation(
                        "Zaprta vodoravna", "P128", 5, RoksalPrivacy.ZASEBNA,
                        "Široka vodoravna deska daje miren enoten videz.",
                        "Preveri razmak stebrov."
                    ),
                    RoksalRecommendation(
                        "Vodoravni ROMB", "ROMB67", 15, RoksalPrivacy.SREDNJA,
                        "ROMB poudari horizontalni vzorec.",
                        "Potrebni so pravilni kotniki in alu jedro."
                    ),
                    RoksalRecommendation(
                        "DESKA 150", "DESKA150", 8, RoksalPrivacy.SREDNJA,
                        "Široka deska zmanjša število vodoravnih linij.",
                        "Izberi KLASIK ali RUSTIK."
                    )
                )
            }
            "OGRAJA" -> if (config.orientation == RoksalOrientation.POKONCNA) {
                listOf(
                    RoksalRecommendation(
                        "Čista zasebna ograja", "P128", 3, RoksalPrivacy.ZASEBNA,
                        "Širša polna deska ustvari bolj zaprt videz.",
                        "Dobra začetna konfiguracija za zasebnost."
                    ),
                    RoksalRecommendation(
                        "Sodoben zračen videz", "ROMB67", 18, RoksalPrivacy.SREDNJA,
                        "ROMB ustvari bolj izrazit vzorec.",
                        "Alu cev v sredini je obvezna."
                    ),
                    RoksalRecommendation(
                        "Klasična ozka fuga", "P100", 8, RoksalPrivacy.SREDNJA,
                        "Polna deska 100 je preprost pokončni sistem.",
                        "Končne mere in konstrukcijo potrdi Roksal."
                    )
                )
            } else {
                listOf(
                    RoksalRecommendation(
                        "Zaprta vodoravna", "P128", 5, RoksalPrivacy.ZASEBNA,
                        "Široka vodoravna deska daje miren enoten videz.",
                        "Preveri razmak stebrov."
                    ),
                    RoksalRecommendation(
                        "Vodoravni ROMB", "ROMB67", 15, RoksalPrivacy.SREDNJA,
                        "ROMB poudari horizontalni vzorec.",
                        "Potrebni so pravilni kotniki in alu jedro."
                    ),
                    RoksalRecommendation(
                        "DESKA 150", "DESKA150", 8, RoksalPrivacy.SREDNJA,
                        "Široka deska zmanjša število horizontalnih linij.",
                        "Izberi KLASIK ali RUSTIK."
                    )
                )
            }
            else -> emptyList()
        }
    }
}
