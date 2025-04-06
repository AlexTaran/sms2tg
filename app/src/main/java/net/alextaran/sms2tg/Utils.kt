package net.alextaran.sms2tg

fun String.escapeTgMarkdown(): String {
    val specialChars = """[_*\[\]()~`>#+\-=|{}.!]"""
    return Regex(specialChars).replace(this, """\\$0""")
}