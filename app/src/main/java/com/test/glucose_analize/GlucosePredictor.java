package com.test.glucose_analize;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Аналитический модуль прогнозирования уровня глюкозы в крови
 * на основе текущего показания сенсора и количества употреблённых
 * хлебных единиц (ХЕ).
 *
 * Используемая модель — упрощённая (регрессионная) модель,
 * применяемая в большинстве образовательных диабет-калькуляторов:
 * подъём гликемии считается пропорциональным количеству съеденных ХЕ.
 *
 * Формула максимального прироста глюкозы:
 *     ΔG = ХЕ * K
 * где K — коэффициент подъёма глюкозы на 1 ХЕ (в среднем 1.5–2.2 ммоль/л,
 * берётся усреднённое справочное значение 2.0 ммоль/л на 1 ХЕ).
 *
 * Для построения кривой во времени используется кусочно-линейная модель:
 *  - от 0 до T_peak минут — линейный рост от текущего уровня до пика;
 *  - от T_peak до T_baseline минут — линейное снижение обратно
 *    к исходному уровню (возврат к базовому значению без внешней
 *    коррекции инсулином/активностью);
 *  - после T_baseline — уровень остаётся равным исходному (базовому).
 *
 * Значения T_peak = 60 мин и T_baseline = 180 мин — усреднённые
 * справочные показатели времени всасывания углеводов и достижения
 * постпрандиального пика гликемии.
 *
 * ВАЖНО: класс реализует упрощённую образовательную модель прогноза
 * (для целей курсовой/учебной практики), а не клиническое средство
 * поддержки принятия решений. Он не рассчитывает дозы инсулина и не
 * заменяет консультацию врача.
 */
public class GlucosePredictor {

    /** Коэффициент подъёма глюкозы (ммоль/л) на 1 съеденную ХЕ. */
    public static final float XE_COEFFICIENT = 2;

    /** Время достижения пика гликемии после еды, в минутах. */
    public static final int TIME_TO_PEAK_MIN = 60;

    /** Время возврата уровня глюкозы к исходному значению, в минутах. */
    public static final int TIME_TO_BASELINE_MIN = 180;
    private static SharedPreferences prefs;


    private GlucosePredictor() {
        // утилитарный класс, экземпляры не создаются
    }
    /**
     * Возвращает прогнозируемый пиковый уровень глюкозы после употребления
     * заданного количества ХЕ.
     *
     * @param currentGlucose текущий уровень глюкозы, ммоль/л
     * @param breadUnits     количество употреблённых хлебных единиц (ХЕ)
     * @return прогнозируемый пиковый уровень глюкозы, ммоль/л
     */
    public static float predictPeakGlucose(float currentGlucose, float breadUnits) {
        validateInputs(currentGlucose, breadUnits);
        float delta = breadUnits * XE_COEFFICIENT;
        return currentGlucose + delta;
    }

    /**
     * Возвращает прогнозируемый уровень глюкозы через заданное количество
     * минут после приёма пищи (для построения графика прогноза).
     *
     * @param currentGlucose   текущий уровень глюкозы, ммоль/л
     * @param breadUnits       количество употреблённых ХЕ
     * @param minutesAfterMeal время после приёма пищи, минуты (>= 0)
     * @return прогнозируемый уровень глюкозы в указанный момент времени, ммоль/л
     */
    public static float predictGlucoseAtTime(float currentGlucose, float breadUnits, int minutesAfterMeal) {
        validateInputs(currentGlucose, breadUnits);
        if (minutesAfterMeal < 0) {
            throw new IllegalArgumentException("Время после приёма пищи не может быть отрицательным");
        }

        float delta = breadUnits * XE_COEFFICIENT;

        if (minutesAfterMeal == 0) {
            return currentGlucose;
        }
        if (minutesAfterMeal <= TIME_TO_PEAK_MIN) {
            float fraction = (float) minutesAfterMeal / TIME_TO_PEAK_MIN;
            return currentGlucose + delta * fraction;
        }
        if (minutesAfterMeal <= TIME_TO_BASELINE_MIN) {
            float peak = currentGlucose + delta;
            float fraction = (float) (minutesAfterMeal - TIME_TO_PEAK_MIN)
                    / (TIME_TO_BASELINE_MIN - TIME_TO_PEAK_MIN);
            return peak - delta * fraction;
        }
        return currentGlucose;
    }

    /**
     * Строит массив точек прогнозной кривой глюкозы с заданным шагом по времени.
     * Удобно для передачи данных в компонент построения графиков (например,
     * MPAndroidChart на клиенте).
     *
     * @param currentGlucose текущий уровень глюкозы, ммоль/л
     * @param breadUnits     количество употреблённых ХЕ
     * @param stepMinutes    шаг по времени между точками, минуты
     * @param totalMinutes   общая продолжительность прогноза, минуты
     * @return массив прогнозируемых значений глюкозы
     */
    public static float[] predictCurve(float currentGlucose, float breadUnits, int stepMinutes, int totalMinutes) {
        if (stepMinutes <= 0 || totalMinutes <= 0) {
            throw new IllegalArgumentException("Шаг и продолжительность должны быть положительными");
        }
        int points = totalMinutes / stepMinutes + 1;
        float[] curve = new float[points];
        for (int i = 0; i < points; i++) {
            int t = i * stepMinutes;
            curve[i] = predictGlucoseAtTime(currentGlucose, breadUnits, t);
        }
        return curve;
    }

    /**
     * Формирует текстовую рекомендацию на основе прогнозируемого значения
     * и индивидуальных границ нормы пользователя (п. 4.1.1 ТЗ —
     * настройка индивидуальной нормы уровня глюкозы).
     *
     * @param predictedGlucose прогнозируемый уровень глюкозы, ммоль/л
     * @return текстовая рекомендация для пользователя
     */
    public static String getAdvice(float predictedGlucose, Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        float upperBound = Float.parseFloat(prefs.getString("upper_limit_glucose", "10"));
        float lowerBound = Float.parseFloat(prefs.getString("lower_limit_glucose", "4"));
        if (predictedGlucose > upperBound) {
            String advice = "Прогноз превышает верхнюю границу вашей нормы. ";
            Toast.makeText(context, advice, Toast.LENGTH_LONG).show();
            return advice;
        }
        if (predictedGlucose < lowerBound) {
            String advice = "Прогноз ниже нижней границы вашей нормы. ";
            Toast.makeText(context, advice, Toast.LENGTH_LONG).show();
            return advice;
        }
        String advice = "Прогнозируемый уровень глюкозы находится в пределах вашей индивидуальной нормы.";
        Toast.makeText(context, advice, Toast.LENGTH_SHORT).show();
        return advice;
    }

    private static void validateInputs(float currentGlucose, float breadUnits) {
        if (currentGlucose < 0) {
            throw new IllegalArgumentException("Текущий уровень глюкозы не может быть отрицательным");
        }
        if (breadUnits < 0) {
            throw new IllegalArgumentException("Количество ХЕ не может быть отрицательным");
        }
    }

    /**
     * Пример использования модуля.
     */
    public static Map<String, List<?>> main(float current, String current_time, float xe, int step ) throws ParseException {
        ArrayList<Float> glucose_values = new ArrayList<>();
        ArrayList<String> date_values= new ArrayList<>();

        float peak = predictPeakGlucose(current, xe);
        float[] curve = predictCurve(current, xe, step, TIME_TO_BASELINE_MIN);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//задаем формат
        Date d = sdf.parse(current_time);//парсим
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        for (int i = 0; i < curve.length; i++) {
            glucose_values.add(curve[i]);
            cal.add(Calendar.MINUTE, step);
            date_values.add(sdf.format(cal.getTime()));
            System.out.printf("t=%3d мин -> %.2f ммоль/л%n", i * step, curve[i]);
        }
        Map<String, List<?>> result = new HashMap<>();
        result.put("glucose", glucose_values);
        result.put("dates", date_values);
        result.put("peak", Collections.singletonList(peak));
        return result;
    }
}

