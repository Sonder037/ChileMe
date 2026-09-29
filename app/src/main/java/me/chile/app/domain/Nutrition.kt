package me.chile.app.domain

/** Grams for the entire consumed portion. Null means unknown, including old records. */
data class Nutrients(val proteinG: Double?=null,val carbsG: Double?=null,val fatG: Double?=null) {
    fun valid(kcal: Int): Boolean {
        val values=listOf(proteinG,carbsG,fatG)
        if(values.any {it!=null && (!it.isFinite() || it !in 0.0..2500.0)})return false
        // A generous tolerance accommodates label rounding, fibre and food-specific energy factors.
        val knownEnergy=(proteinG?:0.0)*4+(carbsG?:0.0)*4+(fatG?:0.0)*9
        return knownEnergy<=kcal*1.35+30
    }
}

// WS/T 578.1—2017 §5.1: protein 10–15%, carbohydrate 50–65%, fat 20–30%.
// https://www.nhc.gov.cn/wjw/yingyang/201710/fdade20feb8144ba921b412944ffb779.shtml
// The existing fields are the range's upper edge for ring scaling, not a daily quota.
data class NutritionReference(val energyKcal: Double,val proteinG: Double,val carbsG: Double,val fatG: Double) {
    val proteinMinG get()=energyKcal*.10/4
    val carbsMinG get()=energyKcal*.50/4
    val fatMinG get()=energyKcal*.20/9
}

/** Selected PAL uses estimated total energy. Legacy profiles retain inactive EER until selection. */
fun nutritionReference(profile: Profile?): NutritionReference? {
    val p=profile?:return null
    if(!p.valid() || p.age !in 19..78 || p.sex=="unspecified")return null
    val energy=p.totalEnergy()?.toDouble() ?: if(p.sex=="male")753.07-10.83*p.age+6.50*p.height+14.10*p.weight
        else 584.90-7.01*p.age+5.72*p.height+11.71*p.weight
    if(energy<=0)return null
    return NutritionReference(energy,energy*.15/4,energy*.65/4,energy*.30/9)
}

data class NutrientProgress(val name: String,val grams: Double?,val target: Double?,val knownItems: Int,val totalItems: Int,val minimum: Double?=null) {
    val ratio: Double? get()=if(grams!=null && target!=null && target>0)grams/target else null
    // Product display thresholds, not clinical upper limits.
    val level: Int get()=when { (ratio?:0.0)>1.5->2;(ratio?:0.0)>1.0->1;else->0 }
    val incomplete: Boolean get()=knownItems<totalItems
}
data class NutritionDay(val parts: List<NutrientProgress>,val reference: NutritionReference?)
fun nutritionDay(meals: List<Meal>,profile: Profile?,date: String): NutritionDay {
    val items=meals.filter {it.date==date}.flatMap {it.items}
    val reference=nutritionReference(profile)
    fun part(name: String,target: Double?,minimum: Double?,value: (Nutrients)->Double?): NutrientProgress {
        val known=items.mapNotNull {value(it.nutrients)}
        return NutrientProgress(name,known.takeIf {it.isNotEmpty()}?.sum(),target,known.size,items.size,minimum)
    }
    return NutritionDay(listOf(part("蛋白质",reference?.proteinG,reference?.proteinMinG){it.proteinG},part("碳水",reference?.carbsG,reference?.carbsMinG){it.carbsG},part("脂肪",reference?.fatG,reference?.fatMinG){it.fatG}),reference)
}
