package com.samohammer.app.util

import java.util.UUID

/** Génère un ID stable (String) pour Unit / Profile. */
fun newUuid(): String = UUID.randomUUID().toString()
