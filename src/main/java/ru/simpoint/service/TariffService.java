package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.Tariff;
import ru.simpoint.dto.TariffRequest;
import ru.simpoint.dto.TariffResponse;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.TariffRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TariffService {

    private final TariffRepository tariffs;

    @Transactional(readOnly = true)
    public List<TariffResponse> listActive(Operator operator) {
        List<Tariff> result = operator == null
                ? tariffs.findByActiveTrueOrderByOperatorAscMonthlyPriceAsc()
                : tariffs.findByOperatorAndActiveTrueOrderByMonthlyPriceAsc(operator);
        return result.stream().map(TariffResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TariffResponse get(Long id) {
        return TariffResponse.from(find(id));
    }

    @Transactional
    public TariffResponse create(TariffRequest request) {
        if (tariffs.existsByOperatorAndNameIgnoreCase(request.operator(), request.name())) {
            throw new BusinessRuleException("Тариф «" + request.name() + "» у оператора "
                    + request.operator().getDisplayName() + " уже есть");
        }
        Tariff tariff = new Tariff();
        apply(tariff, request);
        return TariffResponse.from(tariffs.save(tariff));
    }

    @Transactional
    public TariffResponse update(Long id, TariffRequest request) {
        Tariff tariff = find(id);
        boolean renamed = tariff.getOperator() != request.operator()
                || !tariff.getName().equalsIgnoreCase(request.name());
        if (renamed && tariffs.existsByOperatorAndNameIgnoreCase(request.operator(), request.name())) {
            throw new BusinessRuleException("Тариф «" + request.name() + "» у оператора "
                    + request.operator().getDisplayName() + " уже есть");
        }
        apply(tariff, request);
        return TariffResponse.from(tariff);
    }

    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private Tariff find(Long id) {
        return tariffs.findById(id).orElseThrow(() -> new NotFoundException("Тариф не найден: " + id));
    }

    private void apply(Tariff tariff, TariffRequest request) {
        if (request.unlimitedInternet() && request.internetGb() != null) {
            throw new BusinessRuleException("Для безлимитного интернета объём в ГБ не указывается");
        }
        tariff.setOperator(request.operator());
        tariff.setName(request.name().trim());
        tariff.setMonthlyPrice(request.monthlyPrice());
        tariff.setMinutes(request.minutes());
        tariff.setInternetGb(request.internetGb());
        tariff.setUnlimitedInternet(request.unlimitedInternet());
        tariff.setActive(true);
    }
}
