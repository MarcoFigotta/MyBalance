package com.mybalance.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Salvataggio locale (solo sul telefono) tramite SharedPreferences. */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences("mybalance", Context.MODE_PRIVATE)

    fun loadAccount(): Account? {
        val u = p.getString("acc_user", null) ?: return null
        val e = p.getString("acc_email", null) ?: return null
        val s = p.getString("acc_salt", null) ?: return null
        val h = p.getString("acc_hash", null) ?: return null
        return Account(u, e, s, h)
    }

    fun saveAccount(a: Account) {
        p.edit()
            .putString("acc_user", a.username)
            .putString("acc_email", a.email)
            .putString("acc_salt", a.salt)
            .putString("acc_hash", a.hash)
            .apply()
    }

    var loggedIn: Boolean
        get() = p.getBoolean("logged_in", false)
        set(value) {
            p.edit().putBoolean("logged_in", value).apply()
        }

    fun loadProfile(): Profile? {
        val g = p.getString("goal", null) ?: return null
        val goal = try {
            GoalType.valueOf(g)
        } catch (e: IllegalArgumentException) {
            GoalType.TRACK
        }
        return Profile(
            goal = goal,
            initialCents = p.getLong("initial", 0L),
            currency = p.getString("currency", "EUR") ?: "EUR",
            targetCents = p.getLong("target", 0L)
        )
    }

    fun saveProfile(profile: Profile) {
        p.edit()
            .putString("goal", profile.goal.name)
            .putLong("initial", profile.initialCents)
            .putString("currency", profile.currency)
            .putLong("target", profile.targetCents)
            .apply()
    }

    fun loadTxs(): List<Tx> {
        val raw = p.getString("txs", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Tx>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ro = o.optJSONObject("rule")
                val rule = if (ro == null) null else Recurrence(
                    every = ro.optInt("every", 1),
                    unit = RepeatUnit.valueOf(ro.optString("unit", "WEEK")),
                    weekdays = ro.optInt("weekdays", 0),
                    endType = EndType.valueOf(ro.optString("endType", "NEVER")),
                    endDay = ro.optLong("endDay", 0L),
                    endCount = ro.optInt("endCount", 1)
                )
                out.add(
                    Tx(
                        id = o.getLong("id"),
                        type = TxType.valueOf(o.getString("type")),
                        name = o.getString("name"),
                        cents = o.getLong("cents"),
                        period = Period.valueOf(o.getString("period")),
                        tag = Tag.valueOf(o.getString("tag")),
                        startDay = o.getLong("day"),
                        rule = rule,
                        skipped = o.optJSONArray("skipped")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList(),
                        fundId = if (o.has("fundId")) o.getLong("fundId") else null
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTxs(list: List<Tx>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("type", t.type.name)
            o.put("name", t.name)
            o.put("cents", t.cents)
            o.put("period", t.period.name)
            o.put("tag", t.tag.name)
            o.put("day", t.startDay)
            if (t.skipped.isNotEmpty()) {
                val sa = JSONArray()
                for (s in t.skipped) sa.put(s)
                o.put("skipped", sa)
            }
            val fid = t.fundId
            if (fid != null) o.put("fundId", fid)
            val r = t.rule
            if (r != null) {
                val ro = JSONObject()
                ro.put("every", r.every)
                ro.put("unit", r.unit.name)
                ro.put("weekdays", r.weekdays)
                ro.put("endType", r.endType.name)
                ro.put("endDay", r.endDay)
                ro.put("endCount", r.endCount)
                o.put("rule", ro)
            }
            arr.put(o)
        }
        p.edit().putString("txs", arr.toString()).apply()
    }

    fun loadFunds(): List<Fund> {
        val raw = p.getString("funds", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Fund>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Fund(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        icon = o.optInt("icon", 0),
                        limited = o.optBoolean("limited", true),
                        cents = o.optLong("cents", 0L)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFunds(list: List<Fund>) {
        val arr = JSONArray()
        for (f in list) {
            val o = JSONObject()
            o.put("id", f.id)
            o.put("name", f.name)
            o.put("icon", f.icon)
            o.put("limited", f.limited)
            o.put("cents", f.cents)
            arr.put(o)
        }
        p.edit().putString("funds", arr.toString()).apply()
    }

    var theme: ThemeMode
        get() = try {
            ThemeMode.valueOf(p.getString("theme", "LIGHT") ?: "LIGHT")
        } catch (e: IllegalArgumentException) {
            ThemeMode.LIGHT
        }
        set(value) {
            p.edit().putString("theme", value.name).apply()
        }

    var lang: LangMode
        get() = try {
            LangMode.valueOf(p.getString("lang", "SYSTEM") ?: "SYSTEM")
        } catch (e: IllegalArgumentException) {
            LangMode.SYSTEM
        }
        set(value) {
            p.edit().putString("lang", value.name).apply()
        }

    /** Cancella account, profilo e movimenti (tema e lingua restano). */
    fun clearUserData() {
        p.edit()
            .remove("acc_user").remove("acc_email").remove("acc_salt").remove("acc_hash")
            .remove("logged_in")
            .remove("goal").remove("initial").remove("currency").remove("target")
            .remove("txs").remove("funds")
            .apply()
    }
}
