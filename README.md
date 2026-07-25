# SMS2TG (SMS to Telegram Forwarder)

A lightweight Android application that automatically forwards incoming SMS messages to your Telegram account via a custom Telegram bot.

📥 **[Download Latest APK](https://sms2tg.alextaran.net)**

## 💡 Use Case
This app is extremely useful if you have a rarely used SIM-card (for example, for banking OTPs, old registrations, or a secondary number) that you don't want to carry with you all the time. 

Simply install this app on an unused or spare Android device, insert the SIM-card, connect it to the internet **and a charger**, and leave it at home. You will receive all incoming SMS messages directly in your Telegram app wherever you are.

## ✨ Features
* **Background Forwarding:** Seamlessly listens for incoming SMS and forwards them instantly.
* **Multipart SMS Support:** Smartly combines long, fragmented SMS messages into a single, readable Telegram message.
* **Detailed Info:** Shows the sender's number, SIM slot index, carrier name, and exact time of receipt.
* **Privacy First:** Absolutely no analytics, tracking, or external servers involved. Data goes directly from your device to Telegram's API.

## ⚙️ Setup Instructions

To get started, you need to connect the app to your own private Telegram bot and configure the Android system so it doesn't kill the app in the background.

1. **Create a Bot:** Open [Telegram](https://play.google.com/store/apps/details?id=org.telegram.messenger), search for [@BotFather](https://t.me/BotFather), and send the `/newbot` command. Follow the instructions to get your **Bot Token** (Secret Key).
2. **Get your User ID:** You need your personal numeric Telegram User ID so the bot knows who to forward messages to. You can get it by messaging a bot like [@userinfobot](https://t.me/userinfobot).
3. **Configure the App:** Open [SMS2TG](https://sms2tg.alextaran.net), paste both the **Bot Token** and your **User ID** into the corresponding fields.
4. **Initiate the Chat:** ⚠️ *Crucial step!* Telegram bots cannot initiate conversations with users. You must open your newly created bot in Telegram and send it a `/start` message (or any text) first.
5. **Test the Connection:** Use the "Test" buttons inside the app to verify that notifications are successfully reaching your Telegram account.
6. **System Configuration (Very Important):** 
   * Grant all requested permissions (SMS receive, etc.) inside the app.
   * Go to your device's **Settings -> Apps -> SMS2TG**. 
   * Turn **OFF** the toggle for **"Remove permissions if app is unused"** (or similar, depending on your Android version).
   * *Recommended:* Disable battery optimization for this app to prevent the system from putting it to sleep permanently.

## 🔒 Open Source & Transparency
Handling SMS messages requires sensitive permissions. This app's source code is entirely open-source, allowing anyone to audit it and verify that it strictly performs only the actions described above. It does not read your contacts, and it does not send your messages anywhere other than your specified personal Telegram bot.
