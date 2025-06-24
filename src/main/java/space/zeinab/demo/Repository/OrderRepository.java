package space.zeinab.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import space.zeinab.demo.model.OrderEntity;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}
