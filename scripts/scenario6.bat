@echo off
adb shell am broadcast -n com.akkageugot.demo/com.akkageugot.demo.engine.demo.DemoCommandReceiver -a com.akkageugot.demo.DEMO --es cmd scenario --ei n 6
