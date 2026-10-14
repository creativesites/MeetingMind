package com.craftflowtechnologies.meetingmind.core.biblestudy

import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser

/** How someone might come to the Bible ("How I feel"). One chip each on the Bible home. */
enum class Need(val key: String, val label: String) {
    ANXIOUS("anxious", "Anxious"),
    GRATEFUL("grateful", "Grateful"),
    GRIEVING("grieving", "Grieving"),
    DECISION("decision", "Facing a decision"),
    LONELY("lonely", "Lonely"),
    TEMPTED("tempted", "Tempted"),
    TIRED("tired", "Tired"),
    JOYFUL("joyful", "Joyful");

    companion object {
        fun byKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** A passage chosen for a need, with one plain line on why it fits. */
data class NeedPassage(val reference: ScriptureReference, val why: String)

/**
 * The curated passages behind "How I feel" (ONE_PLACE §3.2): at least eight real passages per need,
 * written as "reference|why it fits". The lines describe what the passage says; they never promise
 * an outcome. Same table for everyone: it knows nothing about the reader.
 */
object NeedPassages {

    private val table: Map<Need, List<String>> = mapOf(
        Need.ANXIOUS to listOf(
            "Philippians 4:6-7|Paul invites us to bring every worry to God with thanks, and describes a peace that guards the heart.",
            "Matthew 6:25-34|Jesus points to birds and flowers and asks what worry can add to a day.",
            "1 Peter 5:6-7|A short invitation to hand our anxieties over, because God cares for us.",
            "Psalm 94:18-19|A psalmist remembers what held him when his thoughts multiplied.",
            "Isaiah 41:10|God speaks to the frightened: do not fear, I am with you.",
            "Psalm 56:3-4|Honest words from someone afraid who chooses to trust.",
            "John 14:27|Jesus leaves his friends a peace unlike the world's, on the night before he is arrested.",
            "Psalm 46:1-3|God as a refuge and help, even when the ground itself gives way.",
            "Psalm 23|The best-loved psalm: a shepherd who leads, restores and stays close in the dark valley.",
            "Luke 12:22-31|Jesus teaches his followers not to be consumed by what they will eat or wear."
        ),
        Need.GRATEFUL to listOf(
            "Psalm 100|A short song that calls everyone to come before God with thanksgiving.",
            "Psalm 103:1-5|David tells his own soul not to forget all God's benefits.",
            "Colossians 3:15-17|Paul asks for a thankful heart in everything we say and do.",
            "1 Thessalonians 5:16-18|Three short lines on joy, prayer and giving thanks in all circumstances.",
            "Psalm 136:1-9|A refrain, repeated in every line: God's love endures forever.",
            "Luke 17:11-19|Ten men are healed, and only one comes back to say thank you.",
            "Psalm 107:1-9|The redeemed are invited to tell how God met them in hunger and wandering.",
            "Psalm 138:1-3|A whole-hearted thank-you for love and faithfulness.",
            "Philippians 1:3-6|Paul thanks God for his friends and for the good work begun in them.",
            "2 Corinthians 9:10-15|Generosity overflows into many thanksgivings to God."
        ),
        Need.GRIEVING to listOf(
            "Psalm 34:17-19|God is near to the brokenhearted and crushed in spirit.",
            "Psalm 42:1-5|A psalmist cries honestly from a downcast soul and talks to himself about hope.",
            "Revelation 21:3-4|A picture of God living with his people and wiping away every tear.",
            "John 11:32-36|Jesus meets Mary's grief, and weeps.",
            "Lamentations 3:19-26|Sorrow and hope held in the same breath: his mercies are new every morning.",
            "2 Corinthians 1:3-5|The God of all comfort comforts us so that we can comfort others.",
            "Matthew 5:4|One line from the Sermon on the Mount: blessed are those who mourn.",
            "Psalm 147:3|God heals the brokenhearted and binds up their wounds.",
            "Isaiah 43:1-3|God calls us by name and promises to be with us through water and fire.",
            "Romans 8:35-39|Paul lists every hardship he can think of and says none can separate us from God's love.",
            "1 Thessalonians 4:13-14|Paul writes about grief that is not without hope."
        ),
        Need.DECISION to listOf(
            "Proverbs 3:5-6|Trust God with all your heart, and do not lean on your own understanding.",
            "James 1:5-8|If anyone lacks wisdom, they are invited to ask God, who gives generously.",
            "Psalm 32:8-9|God promises to counsel and watch over those who listen.",
            "Proverbs 16:1-9|We make our plans, and God directs the steps.",
            "Psalm 119:105|God's word as a lamp for the feet and a light for the path.",
            "Isaiah 30:21|A promise of a voice behind you saying, this is the way.",
            "Proverbs 11:14|There is safety in many advisers.",
            "Romans 12:1-2|Paul describes a renewed mind that can discern what is good.",
            "Psalm 25:4-10|A prayer to be shown God's ways and taught his paths.",
            "Proverbs 2:1-6|Wisdom is treated as treasure worth searching for, and God as the one who gives it."
        ),
        Need.LONELY to listOf(
            "Psalm 68:5-6|God sets the lonely in families.",
            "Deuteronomy 31:6|Be strong and courageous: the Lord goes with you and will never leave you.",
            "Hebrews 13:5-6|A promise: I will never leave you nor forsake you.",
            "Matthew 28:20|The last words of Jesus in Matthew: I am with you always.",
            "Psalm 139:7-10|There is nowhere we can go that God is not already there.",
            "John 14:16-18|Jesus promises not to leave his followers as orphans.",
            "Psalm 27:10|Even if father and mother forsake me, the Lord will receive me.",
            "Ecclesiastes 4:9-12|Two are better than one, a reflection on the gift of company.",
            "Psalm 25:16-18|A prayer from someone lonely and afflicted.",
            "Isaiah 49:15-16|God says he will not forget us, even if a mother could."
        ),
        Need.TEMPTED to listOf(
            "1 Corinthians 10:12-13|No temptation is beyond what we can bear, and God provides a way out.",
            "James 1:12-15|James traces how desire grows into sin, and blesses those who persevere.",
            "Matthew 4:1-11|Jesus is tempted in the wilderness and answers each time from Scripture.",
            "Hebrews 4:14-16|Jesus understands weakness, and we may come to him for mercy and help.",
            "Psalm 119:9-11|How can a young person stay on the path? By treasuring God's word.",
            "Ephesians 6:10-18|Paul describes the armour of God for standing firm.",
            "Romans 6:11-14|Paul urges us not to let sin reign, but to offer ourselves to God.",
            "Matthew 26:40-41|Jesus asks his friends to watch and pray so they do not fall into temptation.",
            "James 4:7-8|Submit to God, resist the devil, and draw near to God.",
            "Proverbs 4:23-27|A call to guard the heart and keep the feet from evil."
        ),
        Need.TIRED to listOf(
            "Matthew 11:28-30|Jesus invites the weary and burdened to come to him and find rest.",
            "Isaiah 40:28-31|Those who wait on the Lord will renew their strength.",
            "Psalm 127:1-2|Unless the Lord builds the house, the builders labour in vain; God gives sleep to those he loves.",
            "Psalm 23|A shepherd who makes us lie down in green pastures and restores the soul.",
            "Exodus 33:14|A single sentence: my presence will go with you, and I will give you rest.",
            "1 Kings 19:4-8|Elijah, exhausted and discouraged, is given food and sleep before anything else.",
            "Galatians 6:9|Let us not become weary in doing good, for at the proper time we will reap.",
            "Psalm 121|Help comes from the Lord, who neither slumbers nor sleeps.",
            "Mark 6:30-32|Jesus takes his busy friends away to a quiet place to rest.",
            "Psalm 62:5-8|My soul finds rest in God alone; pour out your heart to him.",
            "Hebrews 4:9-11|A promise of Sabbath rest for the people of God."
        ),
        Need.JOYFUL to listOf(
            "Psalm 126|A psalm of people who laughed and sang when God restored them.",
            "Nehemiah 8:10|The joy of the Lord is your strength.",
            "Psalm 16:8-11|In your presence there is fullness of joy.",
            "Philippians 4:4-7|Rejoice in the Lord always, Paul writes from prison.",
            "Psalm 30:11-12|Mourning turned to dancing, and a heart that cannot be silent.",
            "Habakkuk 3:17-19|Joy in God even when the fields yield nothing.",
            "John 15:9-11|Jesus speaks of remaining in his love so that his joy may be in us.",
            "Zephaniah 3:17|A picture of God rejoicing over his people with singing.",
            "Romans 15:13|A blessing: may the God of hope fill you with all joy and peace.",
            "Psalm 98|A song that invites all creation to shout for joy.",
            "Isaiah 12:2-6|With joy you will draw water from the wells of salvation."
        )
    )

    /** Every passage for [need]; each entry parses, and the table is checked for that in tests. */
    fun passages(need: Need): List<NeedPassage> = table.getValue(need).mapNotNull { raw ->
        val ref = ScriptureReferenceParser.parse(raw.substringBefore('|')) ?: return@mapNotNull null
        NeedPassage(ref, raw.substringAfter('|'))
    }

    /** The raw table, for tests. */
    internal fun raw(need: Need): List<String> = table.getValue(need)

    /**
     * One passage for [need] on [epochDay]: it changes from day to day and steps by [variant] for
     * "another one", so tapping the same chip twice in a day can give a different passage.
     */
    fun pick(need: Need, epochDay: Long, variant: Int = 0): NeedPassage {
        val list = passages(need)
        return list[Math.floorMod(epochDay + variant.toLong() * 3, list.size.toLong()).toInt()]
    }
}
