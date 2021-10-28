job("warmup data") {
    startOn {
        // run on schedule every day at 5AM
        schedule {
            cron("0 5 * * *")
            enabled = false
        }
    }

    warmup(profileId = "default") {
        requirements {
            workerTags("fleet")
        }
        scriptLocation = "./.space/warmup.sh"
    }
}
