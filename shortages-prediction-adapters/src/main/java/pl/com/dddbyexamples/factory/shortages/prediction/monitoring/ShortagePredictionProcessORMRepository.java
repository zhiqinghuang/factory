package pl.com.dddbyexamples.factory.shortages.prediction.monitoring;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import pl.com.dddbyexamples.factory.product.management.RefNoId;
import pl.com.dddbyexamples.factory.shortages.prediction.ConfigurationParams;
import pl.com.dddbyexamples.factory.shortages.prediction.calculation.ShortageForecasts;
import pl.com.dddbyexamples.factory.shortages.prediction.monitoring.persistence.ShortagesDao;
import pl.com.dddbyexamples.factory.shortages.prediction.monitoring.persistence.ShortagesEntity;
import pl.com.dddbyexamples.tools.TechnicalId;

import java.util.Optional;

@Component
@AllArgsConstructor
class ShortagePredictionProcessORMRepository implements ShortagePredictionProcessRepository {

    private final ShortagesDao dao;
    private final ShortageDiffPolicy policy = ShortageDiffPolicy.ValuesAreNotSame;
    private final ShortageForecasts forecasts;
    private final ConfigurationParams configuration = () -> 14;
    private final ShortageEvents events;

    @Override
    public ShortagePredictionProcess get(RefNoId refNo) {
        Optional<ShortagesEntity> entity = dao.findByRefNo(refNo.getRefNo());
        return new ShortagePredictionProcess(
                entity.map(ShortagesEntity::createId)
                        .orElseGet(() -> ShortagesEntity.createId(refNo)),
                entity.map(ShortagesEntity::getShortage).orElse(null),
                policy, forecasts, configuration, new EventsHandler()
        );
    }

    @Override
    public void save(ShortagePredictionProcess model) {
        // persisted after event
    }

    private void save(NewShortage event) {
        RefNoId refNo = event.getRefNo();

        ShortagesEntity entity = TechnicalId.get(refNo)
                .flatMap(dao::findById)
                .orElseGet(() -> dao.save(new ShortagesEntity(refNo.getRefNo())));
        entity.setShortage(event.getShortage());

        events.emit(event);
    }

    private void delete(ShortageSolved event) {
        TechnicalId.get(event.getRefNo())
                .ifPresent(dao::deleteById);

        events.emit(event);
    }

    private class EventsHandler implements ShortageEvents {
        @Override
        public void emit(NewShortage event) {
            save(event);
        }

        @Override
        public void emit(ShortageSolved event) {
            delete(event);
        }
    }
}
