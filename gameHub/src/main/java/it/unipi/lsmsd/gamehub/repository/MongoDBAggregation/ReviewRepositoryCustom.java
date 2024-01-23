package it.unipi.lsmsd.gamehub.repository.MongoDBAggregation;

import it.unipi.lsmsd.gamehub.DTO.GameDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation;
import it.unipi.lsmsd.gamehub.DTO.ReviewDTOAggregation2;
import it.unipi.lsmsd.gamehub.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewRepositoryCustom {

    //aggregation on the highest 20 like user
    List<ReviewDTOAggregation> findAggregation2();

    //aggregation on the highest avarageScore title
    List<ReviewDTOAggregation2> findAggregation3();





}
