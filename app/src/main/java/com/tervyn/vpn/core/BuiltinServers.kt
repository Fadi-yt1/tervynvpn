package com.tervyn.vpn.core

/**
 * The servers shipped with the app: one per country, selected from the Tervyn lists
 * (https://github.com/svinakraft-maker/FlareFeed) and validated against Xray-core at build time.
 *
 * These are free community servers. Their availability is verified on the device before anything is
 * shown as working, and a slot whose server disappears is repaired from the live lists
 * (see [ServerManager.replaceDeadBuiltins]). Bump [VERSION] whenever this table changes.
 */
object BuiltinServers {
    const val VERSION = 1

    data class Builtin(val countryCode: String, val link: String)

    val all: List<Builtin> = listOf(
        Builtin("US", "vless://1d1eda2e-0bd7-4478-877b-1c5425c6c4dd@48.102.168.211:443?encryption=none&flow=xtls-rprx-vision&pbk=yr_opKG6DGTppNDXkk-DhGSYJZXbb9o-caexk2FZO38&security=reality&sid=8f3c6e2a1b5d4f09&sni=tradingview.com&type=tcp&fp=edge"),
        Builtin("CA", "vless://dc620cd6-a9d8-4a0f-8f18-006b895db77e@66.70.179.198:2053?fp=chrome&pbk=k4l6TxwkBbx9DhZAFpByq5rfCWFSgyOex3f_eFe9CWU&security=reality&sid=83f8317a948bb769&sni=www.cloudflare.com&spx=%2F&type=tcp"),
        Builtin("GB", "trojan://kxHw-Dk-e9dffgzYszMlEqLJ92AnY3Pi@tr-netherlands.unlimited-access.ru:443?security=tls&type=tcp&headerType=none&sni=tr-netherlands.unlimited-access.ru&fp=firefox&alpn=http%2F1.1"),
        Builtin("DE", "vless://4201786b-d99b-4147-a5f0-20d47acddb81@179.254.115.127:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=4935e4t.de25.h1clayd.click&fp=chrome&pbk=MzgB17ZFbKqs8pbC3EscTzHid_ENc-k_Cp6nXl-2knw&sid=6976d670818644cc&type=tcp&headerType=none"),
        Builtin("NL", "vless://6db6b948-655f-4f68-a9e7-865050e05b31@93.152.215.172:443?security=reality&flow=xtls-rprx-vision&fp=firefox&pbk=_bGFsLP1Vw7sK0g6oervHFV00C5O3q7X3x_aOo-zhWI&sid=672cac&sni=93.152.215.172&encryption=none&type=tcp"),
        Builtin("FR", "vless://48ff2b70-e180-582f-8866-d9a2edeed5f5@62.210.91.10:23576?flow=xtls-rprx-vision&fp=chrome&pbk=1y5h2FGWKXTJ9xLPCqPo6Mw7RxoZzh6fGkEQKNxpZ3s&security=reality&sni=fuck.rkn&type=tcp"),
        Builtin("ES", "vless://43948542-5c01-4fc3-bf42-e339e9ca3fbe@2.27.27.33:443?encryption=none&security=reality&sni=pimg.mycdn.me&fp=random&pbk=B4NK8UZflXK2SI7P6wvCbfSw0wspQRO0ElZM30gc_1E&type=xhttp&host=pimg.mycdn.me&path=%2F&mode=auto"),
        Builtin("IT", "vless://29f178f7-f96f-46ba-8fc0-bbcd690ba788@82.118.16.189:443?encryption=none&flow=xtls-rprx-vision&fp=firefox&pbk=zmo2VkZkfjh2kWjaRghXUs39ImQo87LmPhjvzbX3yB4&security=reality&sid=1e9a49dd374c1191&sni=www.cloudflare.com&type=tcp"),
        Builtin("CH", "vless://d199d953-be71-4e19-807b-7df54286733f@45.95.232.233:27020?mode=gun&type=grpc&security=reality&sni=1c.hlclub.tech&fp=firefox&pbk=XxWn04baea-nyjxLud3mjo15Xz1DNwlDOOken4qHNVA&sid=555ac69b22424282&serviceName=grpc-tunnel"),
        Builtin("PL", "vless://52a5a29f-c162-411a-8a37-a3f11b0dc537@20.215.66.17:2083?encryption=none&flow=xtls-rprx-vision&fp=chrome&path=/&pbk=WbgtxLlo2gmTzB4Wzxgh_U7G97d4ENTS10fYIoujET4&security=reality&sid=0123456789abcdef&sni=www.apple.com&type=tcp"),
        Builtin("FI", "vless://b7b41f80-8477-4cc8-affb-2680c2478df8@37.27.202.231:12040?flow=xtls-rprx-vision&security=reality&sni=testspeed-foreign.tci.ir&fp=edge&pbk=yuNMNhM9R953IK0nvJrsfV2qiW8v8RxXLaRUalaETwI&sid=1aac41ca1a05d782&encryption=none&type=tcp"),
        Builtin("SE", "vless://30572b57-f5ab-4eb8-82d4-a24d399b99f6@46.29.255.123:59539?&security=reality&flow=xtls-rprx-vision&pbk=R6mPqCsjZMWauci4LHw7Qp1LGKmBd-sxMXplyOn9bSQ&fp=chrome&sni=cloudflare-dns.com&type=tcp&headerType=none&host=cloudflare-dns.com&path=%2F"),
        Builtin("TR", "vless://a346c521-7a7f-452b-9356-a4893feb5982@62.133.63.45:8444?type=grpc&security=reality&sni=github.com&fp=firefox&pbk=FWitre1jinQR7qz5HQ0q2Q4Gg9xA9B0SyizCziN7Ch0"),
        Builtin("AE", "vless://99c5b713-dedf-4a10-8070-916ff3f49b0e@84.235.245.65:2096?security=tls&type=ws&path=%2F&host=fs.qicai99.com&packetEncoding=xudp&sni=fs.qicai99.com&fp=chrome&encryption=none"),
        Builtin("IN", "vless://1d4e18bd-1f26-4915-b35a-7ace4826e76a@194.61.31.126:2053?security=reality&type=raw&packetEncoding=xudp&sni=www.apple.com&fp=firefox&sid=9980e0609b0d3891&pbk=PuAGLfJy6cu1TNoHuKIKSxPetCbZI41fc8hQDjWPIAU"),
        Builtin("SG", "vless://7d718630-7a1a-4af8-9332-aa0610634e8a@79.98.38.24:443?encryption=none&pbk=tKzo0qk-NJCNnmxmSgP7uGmd9VHKiTG2L-YqXYGj4Dc&security=reality&serviceName=shop.v1.Catalog&sid=203242200f9d16be&sni=api.alder.lalibobi.online&type=grpc&fp=edge"),
        Builtin("HK", "vless://35cbae61-ee10-4cb9-abb8-4950b35e9851@156.226.168.221:35193?security=reality&encryption=none&pbk=d_pn_DMaIxhCkNh80h0Mc7nP94hrlN--I8liOIREDxE&headerType=none&fp=firefox&type=tcp&flow=xtls-rprx-vision&sni=www.apple.com&sid=e8daed2d"),
        Builtin("JP", "vless://814bd064-544d-4255-a070-5705c03f6da9@103.75.118.228:11452?security=reality&type=tcp&packetEncoding=xudp&sni=www.apple.com&fp=chrome&flow=xtls-rprx-vision&sid=48050fab&pbk=k2hPp0tTW0Da-HK94wYpSCLbuK44LfGqC2MSJIM1Ti0&encryption=none"),
        Builtin("KR", "vless://a2de9567-9646-45a9-a410-7303a43bdb6c@151.245.106.49:2053?security=reality&type=tcp&packetEncoding=xudp&sni=www.cloudflare.com&fp=chrome&sid=429499c03d4cc8a0&pbk=F6C33iIbfmt1mUsuLTarIC8ygdHKMqsaGvrSQltyxUo"),
        Builtin("AU", "vless://57beeeb0-03fc-4fc9-94da-7604488a7134@176.97.68.100:443?security=reality&flow=xtls-rprx-vision&sni=www.apple.com&type=tcp&fp=ios&pbk=7GvJVbR6BRhtrc8-rcZvB8qTFUF1LPeM_MmoZ04mBxo&sid=54cca3c1c1c7"),
    )

    val countryCodes: List<String> get() = all.map { it.countryCode }
}
